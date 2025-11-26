package aforo.metering.service;

import aforo.metering.client.SubscriptionClient;
import aforo.metering.client.dto.SubscriptionResponse;
import aforo.metering.dto.MeterRequest;
import aforo.metering.dto.MeterResponse;
import aforo.metering.entity.Invoice;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMeteringService {
    
    private final MeterService meterService;
    private final MeteringQueryRepository meteringQueryRepository;
    private final InvoiceService invoiceService;
    private final SubscriptionClient subscriptionClient;
    
    /**
     * Process metering for a specific subscription and rate plan.
     * This can be called immediately after ingestion completes.
     */
    @Async
    public void processMeteringForSubscription(Long organizationId, String jwtToken, Long subscriptionId, Long ratePlanId, Instant from, Instant to) {
        try {
            log.info("Starting automatic metering for organization {} subscription {} with rate plan {} from {} to {}", 
                    organizationId, subscriptionId, ratePlanId, from, to);
            
            // Set tenant context for the async thread (both org ID and JWT token)
            TenantContext.setOrganizationId(organizationId);
            TenantContext.setJwtToken(jwtToken);
            
            MeterRequest request = MeterRequest.builder()
                    .subscriptionId(subscriptionId)
                    .ratePlanId(ratePlanId)
                    .from(from)
                    .to(to)
                    .build();
            
            MeterResponse response = meterService.estimate(request);
            
            log.info("Metering completed for subscription {}. Total cost: {}", 
                    subscriptionId, response.getTotal());
            
            // Create invoice from metering result
            if (organizationId != null && subscriptionId != null) {
                try {
                    // Fetch subscription details to get the correct customer ID
                    SubscriptionResponse subscription = subscriptionClient.getSubscription(subscriptionId);
                    Long customerId = subscription.getCustomerId();
                    
                    if (customerId == null) {
                        log.error("Subscription {} has no customer ID, cannot create invoice", subscriptionId);
                        return;
                    }
                    
                    log.info("Creating invoice for subscription {}, customer {}, organization {}",
                            subscriptionId, customerId, organizationId);
                    
                    Invoice invoice = invoiceService.createInvoiceFromMeterResponse(
                            response,
                            organizationId,
                            customerId, // Correct customer ID from subscription
                            subscriptionId,
                            ratePlanId,
                            from,
                            to
                    );
                    log.info("Invoice {} created for subscription {}. Total: {}",
                            invoice.getInvoiceNumber(), subscriptionId, invoice.getTotalAmount());
                } catch (Exception e) {
                    log.error("Failed to create invoice for subscription {}: {}",
                            subscriptionId, e.getMessage(), e);
                }
            }
            
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
