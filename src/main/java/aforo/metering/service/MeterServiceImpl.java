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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
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
        
        // Variables to hold the actual from/to dates to use
        java.time.Instant from = request.getFrom();
        java.time.Instant to = request.getTo();

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
            
            // AUTO-DETECTION: If from/to not provided, use subscription's current billing period
            if (from == null || to == null) {
                log.info("Automatic metering mode: fetching subscription {} billing period", subscriptionId);
                
                try {
                    // Use subscription's current billing period
                    String billingPeriodStart = subscription.getCurrentBillingPeriodStart();
                    String billingPeriodEnd = subscription.getCurrentBillingPeriodEnd();
                    
                    // Parse billing period start
                    if (billingPeriodStart != null && !billingPeriodStart.isBlank()) {
                        try {
                            from = parseSubscriptionDateTime(billingPeriodStart);
                            log.info("Using subscription billing period start: {} (UTC)", from);
                        } catch (Exception parseEx) {
                            log.warn("Could not parse billing period start '{}', using fallback: {}", billingPeriodStart, parseEx.getMessage());
                            from = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
                        }
                    } else {
                        from = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
                        log.warn("Subscription {} has no billing period start, using 1 hour ago", subscriptionId);
                    }
                    
                    // Parse billing period end
                    if (billingPeriodEnd != null && !billingPeriodEnd.isBlank()) {
                        try {
                            to = parseSubscriptionDateTime(billingPeriodEnd);
                            log.info("Using subscription billing period end: {} (UTC)", to);
                        } catch (Exception parseEx) {
                            log.warn("Could not parse billing period end '{}', using current time: {}", billingPeriodEnd, parseEx.getMessage());
                            to = java.time.Instant.now();
                        }
                    } else {
                        to = java.time.Instant.now();
                        log.warn("Subscription {} has no billing period end, using current time", subscriptionId);
                    }
                    
                    log.info("Automatic metering for subscription {} from {} to {} (subscription billing period)", 
                            subscriptionId, from, to);
                    
                } catch (Exception e) {
                    log.error("Failed to fetch subscription {} billing period details: {}", subscriptionId, e.getMessage());
                    to = java.time.Instant.now();
                    from = to.minus(1, java.time.temporal.ChronoUnit.HOURS);
                    log.warn("Using fallback time range: {} to {}", from, to);
                }
            } else {
                log.info("Manual metering mode for subscription {} from {} to {}", subscriptionId, from, to);
            }
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
                from,
                to,
                subscriptionId,
                productId,
                ratePlanId,
                metricId
        );

        int actualUsage = eventCount.intValue(); // Actual events from database
        int billedUsage = actualUsage; // Usage used for billing (after freemium/minimum adjustments)
        List<MeterResponse.LineItem> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        
        // Track freemium free units applied
        int freeUnitsApplied = 0;
        
        // STEP 1: Apply Freemium - reduce billed usage before pricing
        if (ratePlan.getFreemiums() != null && !ratePlan.getFreemiums().isEmpty()) {
            int totalFreeUnits = ratePlan.getFreemiums().stream()
                    .map(f -> f.getFreeUnits() != null ? f.getFreeUnits() : 0)
                    .reduce(0, Integer::sum);
            if (totalFreeUnits > 0) {
                freeUnitsApplied = Math.min(totalFreeUnits, actualUsage);
                billedUsage = Math.max(0, actualUsage - freeUnitsApplied);
                log.info("Freemium: Reduced billed usage from {} to {} (applied {} free units)", 
                        actualUsage, billedUsage, freeUnitsApplied);
            }
        }
        
        // STEP 2: Apply Minimum Usage - increase billed usage if below minimum
        int minimumUsageRequired = 0;
        if (ratePlan.getMinimumCommitments() != null && !ratePlan.getMinimumCommitments().isEmpty()) {
            for (RatePlanDTO.MinimumCommitmentDTO commitment : ratePlan.getMinimumCommitments()) {
                if (commitment == null) continue;
                Integer minUsage = commitment.getMinimumUsage();
                if (minUsage != null && minUsage > 0 && minUsage > minimumUsageRequired) {
                    minimumUsageRequired = minUsage;
                }
            }
            if (minimumUsageRequired > 0 && billedUsage < minimumUsageRequired) {
                int shortfall = minimumUsageRequired - billedUsage;
                log.info("Minimum Usage: Increasing billed usage from {} to {} (shortfall: {} units)", 
                        billedUsage, minimumUsageRequired, shortfall);
                billedUsage = minimumUsageRequired;
            }
        }
        
        // Use billedUsage for all pricing calculations below
        int usage = billedUsage;

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

        // STEP 3: Add freemium line item for transparency (already applied to usage above)
        if (freeUnitsApplied > 0) {
            lines.add(build("Freemium Credit", 
                    freeUnitsApplied + " free units applied (actual usage: " + actualUsage + ", billed: " + billedUsage + ")", 
                    BigDecimal.ZERO));
        }

        // STEP 4: Add minimum usage line item for transparency (already applied to usage above)
        if (minimumUsageRequired > 0 && billedUsage >= minimumUsageRequired && actualUsage < minimumUsageRequired) {
            int shortfall = minimumUsageRequired - actualUsage;
            lines.add(build("Minimum Usage Commitment", 
                    "Billed for minimum " + minimumUsageRequired + " units (actual: " + actualUsage + ", added: " + shortfall + ")", 
                    BigDecimal.ZERO));
        }
        
        // STEP 5: Apply Discounts BEFORE minimum charge enforcement
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
        
        // STEP 6: Minimum Charge Enforcement - FINAL FLOOR applied after discounts
        if (ratePlan.getMinimumCommitments() != null && !ratePlan.getMinimumCommitments().isEmpty()) {
            BigDecimal maxMinimumCharge = ratePlan.getMinimumCommitments().stream()
                    .filter(m -> m != null && m.getMinimumAmount() != null)
                    .map(RatePlanDTO.MinimumCommitmentDTO::getMinimumAmount)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            
            if (maxMinimumCharge.compareTo(BigDecimal.ZERO) > 0 && total.compareTo(maxMinimumCharge) < 0) {
                BigDecimal uplift = maxMinimumCharge.subtract(total);
                lines.add(build("Minimum Charge Commitment", 
                        "Final floor adjusted to minimum charge of " + maxMinimumCharge + " (after discounts)", uplift));
                total = maxMinimumCharge;
                log.info("Applied minimum charge floor: increased from {} to {} (uplift: {}) after discounts", 
                        total.subtract(uplift), maxMinimumCharge, uplift);
            }
        }

        return MeterResponse.builder()
                .modelType(ratePlan.getBillingFrequency())
                .eventCount(actualUsage)  // Return actual usage, not billed usage
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
        
        // Calculate usage for each tier
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
        
        // Handle overage units beyond the last tier
        if (remaining > 0 && dto.getOverageUnitRate() != null && dto.getOverageUnitRate().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal overageRate = dto.getOverageUnitRate();
            BigDecimal overageAmt = overageRate.multiply(BigDecimal.valueOf(remaining));
            
            // Find the last tier's max to show proper range in label
            int lastTierMax = tiers.isEmpty() ? 0 : 
                (tiers.get(tiers.size() - 1).getMaxUnits() != null ? tiers.get(tiers.size() - 1).getMaxUnits() : 0);
            int overageStart = lastTierMax + 1;
            int overageEnd = overageStart + remaining - 1;
            
            items.add(build("Overage Units (" + overageStart + "-" + overageEnd + ")", 
                           remaining + " * " + overageRate, overageAmt));
            tot = tot.add(overageAmt);
            log.info("Applied overage rate: {} units beyond tier at {} per unit = {}", 
                    remaining, overageRate, overageAmt);
        }
        
        return new PricingResult(items, tot);
    }

    private PricingResult calcVolume(RatePlanDTO.VolumePricingDTO dto, int usage) {
        List<MeterResponse.LineItem> items = new ArrayList<>();
        BigDecimal tot = BigDecimal.ZERO;
        RatePlanDTO.VolumeTierDTO chosen = null;
        List<RatePlanDTO.VolumeTierDTO> tiers = new ArrayList<>(dto.getTiers());
        tiers.sort(Comparator.comparingInt(t -> t.getMinUnits() != null ? t.getMinUnits() : 0));
        
        // Volume pricing: ALL units are charged at ONE rate based on which tier the total usage falls into
        for (RatePlanDTO.VolumeTierDTO t : tiers) {
            int min = t.getMinUnits() != null ? t.getMinUnits() : 0;
            int max = t.getMaxUnits() != null ? t.getMaxUnits() : Integer.MAX_VALUE;
            if (usage >= min && usage <= max) { 
                chosen = t; 
                log.info("Volume pricing: {} units falls into tier {}-{} @ {} per unit", 
                        usage, min, max, t.getPricePerUnit());
                break; 
            }
        }
        
        // Check if usage exceeds all defined tiers (overage scenario) or is below all tiers
        if (chosen == null && !tiers.isEmpty()) {
            int firstTierMin = tiers.get(0).getMinUnits() != null ? tiers.get(0).getMinUnits() : 0;
            int lastTierMax = tiers.get(tiers.size() - 1).getMaxUnits() != null ? 
                              tiers.get(tiers.size() - 1).getMaxUnits() : 0;
            
            // Usage is below the first tier's minimum
            if (usage < firstTierMin) {
                log.info("Volume pricing: {} units is below first tier (min: {}), charging 0", usage, firstTierMin);
                // Don't choose any tier, leave total as 0
            }
            // Usage exceeds all tiers - apply overage rate
            else if (usage > lastTierMax && dto.getOverageUnitRate() != null && 
                dto.getOverageUnitRate().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal overageRate = dto.getOverageUnitRate();
                tot = overageRate.multiply(BigDecimal.valueOf(usage));
                items.add(build("Volume Overage Charge", usage + " * " + overageRate, tot));
                log.info("Volume pricing overage: {} units exceed last tier, charged at {} per unit", 
                        usage, overageRate);
            } else {
                // Fallback to last tier if no overage rate defined
                chosen = tiers.get(tiers.size() - 1);
                log.warn("Volume pricing: {} units exceed last tier, using last tier rate as fallback", usage);
            }
        }
        
        if (chosen != null) {
            BigDecimal price = chosen.getPricePerUnit() != null ? chosen.getPricePerUnit() : BigDecimal.ZERO;
            int min = chosen.getMinUnits() != null ? chosen.getMinUnits() : 0;
            int max = chosen.getMaxUnits() != null ? chosen.getMaxUnits() : Integer.MAX_VALUE;
            tot = price.multiply(BigDecimal.valueOf(usage));
            items.add(build("Volume Charge (Tier " + min + "-" + max + ")", usage + " * " + price, tot));
        }
        
        return new PricingResult(items, tot);
    }

    private PricingResult calcStair(RatePlanDTO.StairStepPricingDTO dto, int usage) {
        List<MeterResponse.LineItem> items = new ArrayList<>();
        BigDecimal tot = BigDecimal.ZERO;
        
        // Null safety check
        if (dto.getSteps() == null || dto.getSteps().isEmpty()) {
            log.warn("Stair step pricing has no steps defined");
            return new PricingResult(items, tot);
        }
        
        RatePlanDTO.StepDTO chosen = null;
        List<RatePlanDTO.StepDTO> steps = new ArrayList<>(dto.getSteps());
        steps.sort(Comparator.comparingInt(s -> s.getUsageThresholdStart() != null ? s.getUsageThresholdStart() : 0));
        
        // Stair step pricing: Charge a flat fee based on which step the usage falls into
        for (RatePlanDTO.StepDTO s : steps) {
            int start = s.getUsageThresholdStart() != null ? s.getUsageThresholdStart() : 0;
            int end = s.getUsageThresholdEnd() != null ? s.getUsageThresholdEnd() : Integer.MAX_VALUE;
            if (usage >= start && usage <= end) { 
                chosen = s; 
                log.info("Stair step pricing: {} units falls into step {}-{}, flat charge: {}", 
                        usage, start, end, s.getMonthlyCharge());
                break; 
            }
        }
        
        // Check if usage is outside the defined steps
        if (chosen == null && !steps.isEmpty()) {
            int firstStepStart = steps.get(0).getUsageThresholdStart() != null ? 
                                steps.get(0).getUsageThresholdStart() : 0;
            int lastStepEnd = steps.get(steps.size() - 1).getUsageThresholdEnd() != null ? 
                             steps.get(steps.size() - 1).getUsageThresholdEnd() : Integer.MAX_VALUE;
            
            // Usage is below the first step - charge nothing
            if (usage < firstStepStart) {
                log.info("Stair step pricing: {} units is below first step threshold ({}), no charge", 
                        usage, firstStepStart);
                // Leave chosen as null, tot as ZERO
            }
            // Usage exceeds all steps - apply overage or fallback
            else if (usage > lastStepEnd) {
                if (dto.getOverageUnitRate() != null && dto.getOverageUnitRate().compareTo(BigDecimal.ZERO) > 0) {
                    // Apply overage rate
                    BigDecimal overageRate = dto.getOverageUnitRate();
                    tot = overageRate.multiply(BigDecimal.valueOf(usage));
                    items.add(build("Stair Step Overage Charge", usage + " * " + overageRate, tot));
                    log.info("Stair step overage: {} units exceed last step, charged at {} per unit", 
                            usage, overageRate);
                } else {
                    // Fallback to last step only when usage actually exceeds
                    chosen = steps.get(steps.size() - 1);
                    log.warn("Stair step pricing: {} units exceed last step (max: {}), using last step rate as fallback", 
                            usage, lastStepEnd);
                }
            }
        }
        
        if (chosen != null) {
            BigDecimal charge = chosen.getMonthlyCharge() != null ? chosen.getMonthlyCharge() : BigDecimal.ZERO;
            int start = chosen.getUsageThresholdStart() != null ? chosen.getUsageThresholdStart() : 0;
            int end = chosen.getUsageThresholdEnd() != null ? chosen.getUsageThresholdEnd() : 0;
            String endLabel = chosen.getUsageThresholdEnd() != null ? String.valueOf(end) : "∞";
            items.add(build("Stair Step Charge (Step " + start + "-" + endLabel + ")", "Flat fee", charge));
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
    
    /**
     * Parse subscription service datetime string and convert to UTC Instant.
     * Format: "20 Jan, 2026 19:14 IST" -> converts IST to UTC
     * 
     * @param dateTimeStr The datetime string from subscription service
     * @return Instant in UTC timezone
     */
    private java.time.Instant parseSubscriptionDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            throw new IllegalArgumentException("DateTime string cannot be null or empty");
        }
        
        // Replace "IST" with actual timezone ID to ensure proper conversion
        // IST = India Standard Time = Asia/Kolkata = UTC+5:30
        String normalized = dateTimeStr.replace("IST", "Asia/Kolkata");
        
        // Parse with pattern: "dd MMM, yyyy HH:mm" followed by zone ID
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
            .ofPattern("dd MMM, yyyy HH:mm z", java.util.Locale.ENGLISH);
        
        java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.parse(normalized, formatter);
        
        // Convert to UTC Instant
        java.time.Instant utcInstant = zonedDateTime.toInstant();
        
        log.debug("Parsed '{}' -> ZonedDateTime: {} -> UTC Instant: {}", 
                dateTimeStr, zonedDateTime, utcInstant);
        
        return utcInstant;
    }
}
