package aforo.metering.service;

import aforo.metering.client.RatePlanClient;
import aforo.metering.client.SubscriptionClient;
import aforo.metering.client.dto.RatePlanDTO;
import aforo.metering.client.dto.SubscriptionResponse;
import aforo.metering.dto.MeterRequest;
import aforo.metering.dto.MeterResponse;
import aforo.metering.repository.UsageRepository;
import aforo.metering.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MeterServiceImpl implements MeterService {

    private final UsageRepository usageRepository;
    private final RatePlanClient ratePlanClient;
    private final SubscriptionClient subscriptionClient;

    @Override
    public MeterResponse estimate(MeterRequest request) {
        if (request == null)
            throw new IllegalArgumentException("request is required");

        Long orgId = TenantContext.requireOrganizationId();

        Long subscriptionId = request.getSubscriptionId();
        Long productId = request.getProductId();
        Long ratePlanId = request.getRatePlanId();
        Long metricId = request.getBillableMetricId();

        if (subscriptionId != null) {
            SubscriptionResponse subscription = subscriptionClient.getSubscription(subscriptionId);
            if (subscription == null) {
                throw new IllegalStateException("Subscription not found for id " + subscriptionId);
            }
            productId = subscription.getProductId();
            Long subscriptionRatePlanId = subscription.getRatePlanId();
            if (subscriptionRatePlanId == null) {
                throw new IllegalStateException("Subscription " + subscriptionId + " has no rate plan");
            }
            ratePlanId = subscriptionRatePlanId;
        }

        if (ratePlanId == null) {
            throw new IllegalArgumentException("Either subscriptionId or ratePlanId is required");
        }

        // 2) Fetch rate plan config
        RatePlanDTO ratePlan = ratePlanClient.fetchRatePlan(ratePlanId);
        if (ratePlan == null)
            throw new IllegalStateException("Rate plan not found");

        if (metricId == null) {
            metricId = ratePlan.getBillableMetricId();
        }

        // 1) Count billable events from ingestion_event table
        BigDecimal eventCount = usageRepository.countEvents(
                orgId,
                request.getFrom(),
                request.getTo(),
                subscriptionId,
                productId,
                ratePlanId,
                metricId
        );

        int usage = eventCount.intValue(); // Each event counts as 1 unit
        List<MeterResponse.LineItem> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // --- BASE PRICING MODELS (copied from estimator) ---
        if (ratePlan.getFlatFee() != null) {
            RatePlanDTO.FlatFeeDTO flat = ratePlan.getFlatFee();
            BigDecimal base = flat.getFlatFeeAmount() != null ? flat.getFlatFeeAmount() : BigDecimal.ZERO;
            lines.add(build("Flat Fee", "Base", base));
            total = total.add(base);

            int included = flat.getIncludedUnits() != null ? flat.getIncludedUnits() : 0;
            BigDecimal overRate = flat.getOverageRate() != null ? flat.getOverageRate() : BigDecimal.ZERO;
            int overUnits = Math.max(0, usage - included);
            if (overUnits > 0 && overRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal overAmt = overRate.multiply(BigDecimal.valueOf(overUnits));
                lines.add(build("Overage Charges", overUnits + " * " + overRate, overAmt));
                total = total.add(overAmt);
            }
        }

        if (ratePlan.getUsageBasedPricings() != null && !ratePlan.getUsageBasedPricings().isEmpty()) {
            for (RatePlanDTO.UsageBasedPricingDTO ub : ratePlan.getUsageBasedPricings()) {
                BigDecimal per = ub.getPricePerUnit() != null ? ub.getPricePerUnit() : BigDecimal.ZERO;
                BigDecimal amt = per.multiply(BigDecimal.valueOf(usage));
                lines.add(build("Usage Charges", per + " * " + usage, amt));
                total = total.add(amt);
            }
        }

        if (ratePlan.getTieredPricings() != null && !ratePlan.getTieredPricings().isEmpty()) {
            for (RatePlanDTO.TieredPricingDTO tiered : ratePlan.getTieredPricings()) {
                PricingResult tierRes = calcTiered(tiered, usage);
                lines.addAll(tierRes.lines);
                total = total.add(tierRes.total);
            }
        }

        if (ratePlan.getVolumePricings() != null && !ratePlan.getVolumePricings().isEmpty()) {
            for (RatePlanDTO.VolumePricingDTO vol : ratePlan.getVolumePricings()) {
                PricingResult volRes = calcVolume(vol, usage);
                lines.addAll(volRes.lines);
                total = total.add(volRes.total);
            }
        }

        if (ratePlan.getStairStepPricings() != null && !ratePlan.getStairStepPricings().isEmpty()) {
            for (RatePlanDTO.StairStepPricingDTO ss : ratePlan.getStairStepPricings()) {
                PricingResult ssRes = calcStair(ss, usage);
                lines.addAll(ssRes.lines);
                total = total.add(ssRes.total);
            }
        }

        // Extras: Setup, Discounts, Freemium, Commitment (same as estimator) -- omitted for brevity
        // TODO copy remaining extras logic here if needed.
        // Setup Fee
        if (ratePlan.getSetupFees() != null && !ratePlan.getSetupFees().isEmpty()) {
            BigDecimal setupSum = ratePlan.getSetupFees().stream()
                    .map(f -> f.getAmount() != null ? f.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (setupSum.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(build("Setup Fee", "Fixed", setupSum));
                total = total.add(setupSum);
            }
        }

        // Freemium
        if (ratePlan.getFreemiums() != null && !ratePlan.getFreemiums().isEmpty()) {
            int freeUnits = ratePlan.getFreemiums().stream()
                    .map(f -> f.getFreeUnits() != null ? f.getFreeUnits() : 0)
                    .reduce(0, Integer::sum);
            if (freeUnits > 0) {
                BigDecimal perUnit = determinePerUnit(ratePlan);
                if (perUnit != null && perUnit.compareTo(BigDecimal.ZERO) > 0) {
                    int appliedFree = Math.min(freeUnits, usage);
                    BigDecimal credit = perUnit.multiply(BigDecimal.valueOf(appliedFree));
                    if (credit.compareTo(BigDecimal.ZERO) > 0) {
                        lines.add(build("Freemium Credit", appliedFree + " free units", credit.negate()));
                        total = total.subtract(credit);
                    }
                }
            }
        }

        // Minimum Commitment
        if (ratePlan.getMinimumCommitments() != null && !ratePlan.getMinimumCommitments().isEmpty()) {
            BigDecimal minAmt = ratePlan.getMinimumCommitments().stream()
                    .map(m -> m.getMinimumAmount() != null ? m.getMinimumAmount() : BigDecimal.ZERO)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            if (minAmt.compareTo(BigDecimal.ZERO) > 0 && total.compareTo(minAmt) < 0) {
                BigDecimal uplift = minAmt.subtract(total);
                lines.add(build("Minimum Commitment Uplift", "Adjusted to minimum", uplift));
                total = minAmt;
            }
        }

        // Discounts
        if (ratePlan.getDiscounts() != null && !ratePlan.getDiscounts().isEmpty()) {
            LocalDate today = LocalDate.now();
            for (RatePlanDTO.DiscountDTO d : ratePlan.getDiscounts()) {
                if (d == null) continue;
                if (d.getStartDate() != null && today.isBefore(d.getStartDate())) continue;
                if (d.getEndDate() != null && today.isAfter(d.getEndDate())) continue;

                BigDecimal discountAmt = BigDecimal.ZERO;
                String label = "Discount";
                if ("PERCENTAGE".equalsIgnoreCase(d.getDiscountType())) {
                    BigDecimal pct = d.getPercentage();
                    if (pct != null && pct.compareTo(BigDecimal.ZERO) > 0) {
                        discountAmt = total.multiply(pct)
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        label = "Discount (" + pct + "%)";
                    }
                } else if ("FLAT".equalsIgnoreCase(d.getDiscountType())) {
                    BigDecimal flat = d.getFlatAmount();
                    if (flat != null && flat.compareTo(BigDecimal.ZERO) > 0) {
                        discountAmt = flat;
                        label = "Flat Discount";
                    }
                } else {
                    if (d.getFlatAmount() != null && d.getFlatAmount().compareTo(BigDecimal.ZERO) > 0) {
                        discountAmt = d.getFlatAmount();
                        label = "Flat Discount";
                    } else if (d.getPercentage() != null && d.getPercentage().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal pct = d.getPercentage();
                        discountAmt = total.multiply(pct)
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        label = "Discount (" + pct + "%)";
                    }
                }

                if (discountAmt.compareTo(BigDecimal.ZERO) > 0) {
                    if (discountAmt.compareTo(total) > 0) {
                        discountAmt = total;
                    }
                    lines.add(build(label, label, discountAmt.negate()));
                    total = total.subtract(discountAmt);
                }
            }
        }

        return MeterResponse.builder()
                .modelType(ratePlan.getBillingFrequency())
                .breakdown(lines)
                .total(total.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    // --- Helpers ---
    private PricingResult calcTiered(RatePlanDTO.TieredPricingDTO dto, int usage) {
        List<MeterResponse.LineItem> items = new ArrayList<>();
        BigDecimal tot = BigDecimal.ZERO;
        List<RatePlanDTO.TierDTO> tiers = new ArrayList<>(dto.getTiers());
        tiers.sort(Comparator.comparingInt(t -> t.getMinUnits() != null ? t.getMinUnits() : 0));
        int remaining = usage;
        for (RatePlanDTO.TierDTO t : tiers) {
            if (remaining <= 0) break;
            int min = t.getMinUnits() != null ? t.getMinUnits() : 0;
            int max = t.getMaxUnits() != null ? t.getMaxUnits() : Integer.MAX_VALUE;
            BigDecimal price = t.getPricePerUnit() != null ? t.getPricePerUnit() : BigDecimal.ZERO;
            if (usage >= min) {
                int units = Math.min(remaining, max - min + 1);
                BigDecimal amt = price.multiply(BigDecimal.valueOf(units));
                items.add(build("Tier " + min + "-" + max, units + " * " + price, amt));
                tot = tot.add(amt);
                remaining -= units;
            }
        }
        return new PricingResult(items, tot);
    }

    private PricingResult calcVolume(RatePlanDTO.VolumePricingDTO dto, int usage) {
        List<MeterResponse.LineItem> items = new ArrayList<>();
        BigDecimal tot = BigDecimal.ZERO;
        RatePlanDTO.VolumeTierDTO chosen = null;
        List<RatePlanDTO.VolumeTierDTO> tiers = new ArrayList<>(dto.getTiers());
        tiers.sort(Comparator.comparingInt(t -> t.getMinUnits() != null ? t.getMinUnits() : 0));
        for (RatePlanDTO.VolumeTierDTO t : tiers) {
            int min = t.getMinUnits() != null ? t.getMinUnits() : 0;
            int max = t.getMaxUnits() != null ? t.getMaxUnits() : Integer.MAX_VALUE;
            if (usage >= min && usage <= max) { chosen = t; break; }
        }
        if (chosen == null && !tiers.isEmpty()) chosen = tiers.get(tiers.size()-1);
        if (chosen != null) {
            BigDecimal price = chosen.getPricePerUnit() != null ? chosen.getPricePerUnit() : BigDecimal.ZERO;
            tot = price.multiply(BigDecimal.valueOf(usage));
            items.add(build("Volume Charge", usage + " * " + price, tot));
        }
        return new PricingResult(items, tot);
    }

    private PricingResult calcStair(RatePlanDTO.StairStepPricingDTO dto, int usage) {
        List<MeterResponse.LineItem> items = new ArrayList<>();
        BigDecimal tot = BigDecimal.ZERO;
        RatePlanDTO.StepDTO chosen = null;
        List<RatePlanDTO.StepDTO> steps = new ArrayList<>(dto.getSteps());
        steps.sort(Comparator.comparingInt(s -> s.getUsageThresholdStart() != null ? s.getUsageThresholdStart() : 0));
        for (RatePlanDTO.StepDTO s : steps) {
            int start = s.getUsageThresholdStart() != null ? s.getUsageThresholdStart() : 0;
            int end = s.getUsageThresholdEnd() != null ? s.getUsageThresholdEnd() : Integer.MAX_VALUE;
            if (usage >= start && usage <= end) { chosen = s; break; }
        }
        if (chosen == null && !steps.isEmpty()) chosen = steps.get(steps.size()-1);
        if (chosen != null) {
            BigDecimal charge = chosen.getMonthlyCharge() != null ? chosen.getMonthlyCharge() : BigDecimal.ZERO;
            items.add(build("Stair Step Charge", chosen.getUsageThresholdStart() + "-" + (chosen.getUsageThresholdEnd()!=null? chosen.getUsageThresholdEnd():"∞"), charge));
            tot = charge;
        }
        return new PricingResult(items, tot);
    }

    private MeterResponse.LineItem build(String label, String calc, BigDecimal amt) {
        return MeterResponse.LineItem.builder().label(label).calculation(calc).amount(amt).build();
    }

    private record PricingResult(List<MeterResponse.LineItem> lines, BigDecimal total) {}

    private BigDecimal determinePerUnit(RatePlanDTO ratePlan) {
        if (ratePlan.getUsageBasedPricings() != null && !ratePlan.getUsageBasedPricings().isEmpty()) {
            BigDecimal per = ratePlan.getUsageBasedPricings().get(0).getPricePerUnit();
            if (per != null) return per;
        }
        if (ratePlan.getFlatFee() != null && ratePlan.getFlatFee().getOverageRate() != null) {
            return ratePlan.getFlatFee().getOverageRate();
        }
        if (ratePlan.getVolumePricings() != null && !ratePlan.getVolumePricings().isEmpty()) {
            List<RatePlanDTO.VolumeTierDTO> tiers = new ArrayList<>(ratePlan.getVolumePricings().get(0).getTiers());
            tiers.sort(Comparator.comparingInt(t -> t.getMinUnits() != null ? t.getMinUnits() : 0));
            for (RatePlanDTO.VolumeTierDTO t : tiers) {
                if (t.getPricePerUnit() != null) return t.getPricePerUnit();
            }
        }
        if (ratePlan.getTieredPricings() != null && !ratePlan.getTieredPricings().isEmpty()) {
            List<RatePlanDTO.TierDTO> tiers = new ArrayList<>(ratePlan.getTieredPricings().get(0).getTiers());
            tiers.sort(Comparator.comparingInt(t -> t.getMinUnits() != null ? t.getMinUnits() : 0));
            for (RatePlanDTO.TierDTO t : tiers) {
                if (t.getPricePerUnit() != null) return t.getPricePerUnit();
            }
        }
        return BigDecimal.ZERO;
    }
}
