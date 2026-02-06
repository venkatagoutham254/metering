package aforo.metering.analytics.service;

import aforo.metering.analytics.dto.*;
import aforo.metering.analytics.repository.MeteringAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeteringAnalyticsService {

    private final MeteringAnalyticsRepository analyticsRepository;

    /**
     * Get customer usage for a specific period with comparison to previous period
     */
    public CustomerUsageResponse getCustomerUsage(Long organizationId, Long customerId, Instant startDate, Instant endDate) {
        log.debug("Getting customer usage for customer: {} in org: {}", customerId, organizationId);

        // Current period usage
        BigDecimal currentUsage = analyticsRepository.getCustomerUsage(organizationId, customerId, startDate, endDate);

        // Previous period (same duration before startDate)
        long periodDays = Duration.between(startDate, endDate).toDays();
        Instant previousStart = startDate.minus(periodDays, ChronoUnit.DAYS);
        Instant previousEnd = startDate;
        
        BigDecimal previousUsage = analyticsRepository.getCustomerUsage(organizationId, customerId, previousStart, previousEnd);

        // Calculate change percentage
        double changePercentage = calculateChangePercentage(currentUsage.doubleValue(), previousUsage.doubleValue());

        String period = String.format("%s to %s", startDate, endDate);

        return CustomerUsageResponse.builder()
                .customerId(customerId)
                .currentPeriodUsage(currentUsage)
                .previousPeriodUsage(previousUsage)
                .usageChangePercentage(changePercentage)
                .period(period)
                .build();
    }

    /**
     * Get usage trends for multiple customers
     */
    public UsageTrendsResponse getUsageTrends(Long organizationId, List<Long> customerIds, Instant startDate, Instant endDate) {
        log.debug("Getting usage trends for {} customers", customerIds != null ? customerIds.size() : 0);

        if (customerIds == null || customerIds.isEmpty()) {
            return UsageTrendsResponse.builder().trends(new ArrayList<>()).build();
        }

        // Get current period usage
        Map<Long, BigDecimal> currentUsageMap = analyticsRepository.getCustomersUsage(
                organizationId, customerIds, startDate, endDate);

        // Get previous period usage
        long periodDays = Duration.between(startDate, endDate).toDays();
        Instant previousStart = startDate.minus(periodDays, ChronoUnit.DAYS);
        Instant previousEnd = startDate;
        
        Map<Long, BigDecimal> previousUsageMap = analyticsRepository.getCustomersUsage(
                organizationId, customerIds, previousStart, previousEnd);

        // Build trend response
        List<UsageTrendsResponse.UsageTrend> trends = new ArrayList<>();
        for (Long customerId : customerIds) {
            Double currentUsage = currentUsageMap.getOrDefault(customerId, BigDecimal.ZERO).doubleValue();
            Double previousUsage = previousUsageMap.getOrDefault(customerId, BigDecimal.ZERO).doubleValue();
            Double changePercentage = calculateChangePercentage(currentUsage, previousUsage);
            String trendDirection = determineTrendDirection(changePercentage);

            trends.add(UsageTrendsResponse.UsageTrend.builder()
                    .customerId(customerId)
                    .currentUsage(currentUsage)
                    .previousUsage(previousUsage)
                    .changePercentage(changePercentage)
                    .trendDirection(trendDirection)
                    .build());
        }

        return UsageTrendsResponse.builder().trends(trends).build();
    }

    /**
     * Get customer activity (last activity timestamp)
     */
    public CustomerActivityResponse getCustomerActivity(Long organizationId, Long customerId) {
        log.debug("Getting customer activity for customer: {}", customerId);

        Instant lastActivity = analyticsRepository.getLastActivityTimestamp(organizationId, customerId);
        
        Long daysSinceLastActivity = null;
        String activityStatus = "NEVER_ACTIVE";

        if (lastActivity != null) {
            daysSinceLastActivity = Duration.between(lastActivity, Instant.now()).toDays();
            activityStatus = determineActivityStatus(daysSinceLastActivity);
        }

        return CustomerActivityResponse.builder()
                .customerId(customerId)
                .lastActivityTimestamp(lastActivity)
                .daysSinceLastActivity(daysSinceLastActivity)
                .activityStatus(activityStatus)
                .build();
    }

    /**
     * Get usage change percentage for a customer
     */
    public Double getUsageChangePercentage(Long organizationId, Long customerId, Instant startDate, Instant endDate) {
        CustomerUsageResponse usage = getCustomerUsage(organizationId, customerId, startDate, endDate);
        return usage.getUsageChangePercentage();
    }

    // ========== HELPER METHODS ==========

    private double calculateChangePercentage(double current, double previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((current - previous) / previous) * 100.0;
    }

    private String determineTrendDirection(double changePercentage) {
        if (changePercentage > 5.0) {
            return "UP";
        } else if (changePercentage < -5.0) {
            return "DOWN";
        } else {
            return "STABLE";
        }
    }

    private String determineActivityStatus(long daysSinceLastActivity) {
        if (daysSinceLastActivity <= 7) {
            return "ACTIVE";
        } else if (daysSinceLastActivity <= 30) {
            return "INACTIVE";
        } else {
            return "DORMANT";
        }
    }
}
