package aforo.metering.service;

import aforo.metering.client.SubscriptionClient;
import aforo.metering.client.dto.SubscriptionResponse;
import aforo.metering.dto.MeterRequest;
import aforo.metering.dto.MeterResponse;
import aforo.metering.repository.MeteringQueryRepository;
import aforo.metering.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service to automatically trigger metering calculations.
 * Can be called after ingestion completes or run on a schedule.
 * Note: Invoice creation is handled separately by BillingCycleScheduler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMeteringService {
    
    private final MeterService meterService;
    private final MeteringQueryRepository meteringQueryRepository;
    private final SubscriptionClient subscriptionClient;
    
    /**
     * Process metering for a specific subscription and return results synchronously.
     * Used by trigger endpoint to show calculation results in response body.
     * 
     * Supports two modes:
     * 1. Automatic mode: from/to are null -> uses subscription's current billing period
     * 2. Manual mode: from/to are provided -> uses specified time range
     * 
     * @param organizationId Organization ID
     * @param jwtToken JWT token for authentication
     * @param subscriptionId Subscription ID to meter
     * @param from Start time (optional - if null, uses subscription's billing period start)
     * @param to End time (optional - if null, uses current time)
     * @return MeterResponse with cost breakdown and line items
     */
    public MeterResponse processMeteringForSubscriptionSync(Long organizationId, String jwtToken, Long subscriptionId, Instant from, Instant to) {
        try {
            // Set tenant context
            TenantContext.setOrganizationId(organizationId);
            TenantContext.setJwtToken(jwtToken);
            
            // If from/to not provided, fetch subscription to get current billing period
            if (from == null || to == null) {
                log.info("Automatic metering mode: fetching subscription {} billing period", subscriptionId);
                
                try {
                    SubscriptionResponse subscription = subscriptionClient.getSubscription(subscriptionId);
                    
                    // Use subscription's current billing period (now as Instant)
                    Instant billingPeriodStart = subscription.getCurrentBillingPeriodStart();
                    Instant billingPeriodEnd = subscription.getCurrentBillingPeriodEnd();
                    
                    // Use billing period start
                    if (billingPeriodStart != null) {
                        from = billingPeriodStart;
                        log.info("Using subscription billing period start: {}", from);
                    } else {
                        from = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
                        log.warn("Subscription {} has no billing period start, using 1 hour ago", subscriptionId);
                    }
                    
                    // Use billing period end
                    if (billingPeriodEnd != null) {
                        to = billingPeriodEnd;
                        log.info("Using subscription billing period end: {}", to);
                    } else {
                        to = Instant.now();
                        log.warn("Subscription {} has no billing period end, using current time", subscriptionId);
                    }
                    
                    log.info("Automatic metering for subscription {} from {} to {} (subscription billing period)", 
                            subscriptionId, from, to);
                    
                } catch (Exception e) {
                    log.error("Failed to fetch subscription {} for billing period: {}", subscriptionId, e.getMessage());
                    to = Instant.now();
                    from = to.minus(1, java.time.temporal.ChronoUnit.HOURS);
                    log.warn("Using fallback time range: {} to {}", from, to);
                }
            } else {
                log.info("Manual metering mode for subscription {} from {} to {}", subscriptionId, from, to);
            }
            
            MeterRequest request = MeterRequest.builder()
                    .subscriptionId(subscriptionId)
                    .from(from)
                    .to(to)
                    .build();
            
            MeterResponse response = meterService.estimate(request);
            
            log.info("Metering completed for subscription {}. Total cost: {} for period {} to {}", 
                    subscriptionId, response.getTotal(), from, to);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error processing metering for subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("Failed to process metering for subscription " + subscriptionId, e);
        } finally {
            TenantContext.clear();
        }
    }
    
    /**
     * Process metering for a specific subscription asynchronously (for background processing).
     * This can be called immediately after ingestion completes.
     * 
     * Supports two modes:
     * 1. Automatic mode: from/to are null -> uses subscription's current billing period
     * 2. Manual mode: from/to are provided -> uses specified time range
     * 
     * @param organizationId Organization ID
     * @param jwtToken JWT token for authentication
     * @param subscriptionId Subscription ID to meter
     * @param from Start time (optional - if null, uses subscription's billing period start)
     * @param to End time (optional - if null, uses current time)
     */
    @Async
    public void processMeteringForSubscription(Long organizationId, String jwtToken, Long subscriptionId, Instant from, Instant to) {
        try {
            // Set tenant context for the async thread (both org ID and JWT token)
            TenantContext.setOrganizationId(organizationId);
            TenantContext.setJwtToken(jwtToken);
            
            // If from/to not provided, fetch subscription to get current billing period
            if (from == null || to == null) {
                log.info("Automatic metering mode: fetching subscription {} billing period", subscriptionId);
                
                try {
                    SubscriptionResponse subscription = subscriptionClient.getSubscription(subscriptionId);
                    
                    // Use subscription's billing period or creation time (now as Instant)
                    Instant billingPeriodStart = subscription.getCurrentBillingPeriodStart();
                    Instant createdOn = subscription.getCreatedOn();
                    
                    // Prefer billing period start, fallback to creation time
                    if (billingPeriodStart != null) {
                        from = billingPeriodStart;
                        log.info("Using subscription billing period start: {}", from);
                    } else if (createdOn != null) {
                        from = createdOn;
                        log.info("Using subscription creation time as billing period start: {}", from);
                    } else {
                        // Fallback if neither available
                        from = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
                        log.warn("Subscription {} has no date information, using 1 hour ago", subscriptionId);
                    }
                    
                    // Use current time as 'to' for cumulative calculation
                    to = Instant.now();
                    
                    log.info("Automatic metering for subscription {} from {} to {} (current billing period)", 
                            subscriptionId, from, to);
                    
                } catch (Exception e) {
                    log.error("Failed to fetch subscription {} for billing period: {}", subscriptionId, e.getMessage());
                    // Fallback to 1 hour window
                    to = Instant.now();
                    from = to.minus(1, java.time.temporal.ChronoUnit.HOURS);
                    log.warn("Using fallback time range: {} to {}", from, to);
                }
            } else {
                log.info("Manual metering mode for subscription {} from {} to {}", subscriptionId, from, to);
            }
            
            MeterRequest request = MeterRequest.builder()
                    .subscriptionId(subscriptionId)
                    .from(from)
                    .to(to)
                    .build();
            
            MeterResponse response = meterService.estimate(request);
            
            log.info("Metering completed for subscription {}. Total cost: {} for period {} to {}", 
                    subscriptionId, response.getTotal(), from, to);
            
            // Note: Invoice creation is commented out for now (will be handled by BillingCycleScheduler later)
            // This webhook is only for calculating and viewing current usage
            log.debug("Metering calculation complete. Invoice creation will be handled by billing cycle scheduler.");
            
        } catch (Exception e) {
            log.error("Error processing metering for subscription {}: {}", subscriptionId, e.getMessage(), e);
        } finally {
            // Clear tenant context to prevent memory leaks
            TenantContext.clear();
        }
    }
    
    /**
     * Process metering for the current billing period.
     * This runs hourly to catch any newly ingested events.
     */
    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void processPeriodicalMetering() {
        try {
            log.info("Starting scheduled metering process");
            
            // Calculate for the current month
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            
            Instant from = startOfMonth.atZone(ZoneId.systemDefault()).toInstant();
            Instant to = now.atZone(ZoneId.systemDefault()).toInstant();
            
            // Get organization from context (may need to be set externally for scheduled tasks)
            Long orgId = TenantContext.getOrganizationId();
            if (orgId == null) {
                log.warn("No organization context for scheduled metering, skipping");
                return;
            }
            
            // Check if there are new events to process
            Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
            if (!meteringQueryRepository.hasNewEvents(orgId, oneHourAgo)) {
                log.debug("No new events in the last hour, skipping metering");
                return;
            }
            
            // Query distinct rate plans that have events
            List<Long> ratePlanIds = meteringQueryRepository.findDistinctRatePlanIds(orgId, from, to);
            
            if (ratePlanIds.isEmpty()) {
                log.info("No rate plans found with events for period {} to {}", from, to);
                return;
            }
            
            log.info("Found {} rate plans to process for period {} to {}", ratePlanIds.size(), from, to);
            
            // Process each rate plan
            for (Long ratePlanId : ratePlanIds) {
                try {
                    MeterRequest request = MeterRequest.builder()
                            .ratePlanId(ratePlanId)
                            .from(from)
                            .to(to)
                            .build();
                    
                    MeterResponse response = meterService.estimate(request);
                    
                    log.info("Metering completed for rate plan {}. Total cost: {}", 
                            ratePlanId, response.getTotal());
                    
                    // Create invoice from metering result
                    // Note: For scheduled jobs without subscription context, we skip invoice creation
                    // Invoices should be created through explicit API calls or subscription-based triggers
                    log.debug("Skipping invoice creation for rate plan {} (no subscription context)", ratePlanId);
                    
                } catch (Exception e) {
                    log.error("Error processing metering for rate plan {}: {}", ratePlanId, e.getMessage());
                }
            }
            
            log.info("Scheduled metering completed for period {} to {}", from, to);
            
        } catch (Exception e) {
            log.error("Error in scheduled metering process: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Process metering for all subscriptions in a batch.
     * This can be called from an API endpoint or event listener.
     */
    public void processBatchMetering(List<Long> ratePlanIds, Instant from, Instant to) {
        log.info("Starting batch metering for {} rate plans", ratePlanIds.size());
        
        for (Long ratePlanId : ratePlanIds) {
            try {
                MeterRequest request = MeterRequest.builder()
                        .ratePlanId(ratePlanId)
                        .from(from)
                        .to(to)
                        .build();
                
                MeterResponse response = meterService.estimate(request);
                
                log.info("Metering completed for rate plan {}. Total cost: {}", 
                        ratePlanId, response.getTotal());
                
                // For batch processing, results are calculated but invoices are not auto-created
                // Invoices should be created explicitly via API endpoints
                log.debug("Metering calculated for rate plan {}. Use invoice API to create invoice.", ratePlanId);
                
            } catch (Exception e) {
                log.error("Error processing metering for rate plan {}: {}", ratePlanId, e.getMessage());
            }
        }
        
        log.info("Batch metering completed");
    }
}
