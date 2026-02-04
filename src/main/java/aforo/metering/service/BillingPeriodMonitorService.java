package aforo.metering.service;

import aforo.metering.client.SubscriptionClient;
import aforo.metering.client.dto.SubscriptionResponse;
import aforo.metering.dto.MeterRequest;
import aforo.metering.dto.MeterResponse;
import aforo.metering.entity.Invoice;
import aforo.metering.repository.InvoiceRepository;
import aforo.metering.repository.OrganizationRepository;
import aforo.metering.tenant.TenantContext;
import aforo.metering.util.JwtTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service that monitors subscription billing periods and automatically generates invoices
 * when periods end. Runs every 10 minutes to catch hourly billing cycles.
 * 
 * Flow:
 * 1. Get all organizations from ingestion_event table
 * 2. For each organization, fetch all active subscriptions
 * 3. Check if billing period has ended (currentBillingPeriodEnd <= now)
 * 4. If ended and no invoice exists, generate invoice automatically
 * 5. QuickBooks webhook fires automatically (handled by InvoiceService)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingPeriodMonitorService {

    private final OrganizationRepository organizationRepository;
    private final SubscriptionClient subscriptionClient;
    private final MeterService meterService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final JwtTokenGenerator jwtTokenGenerator;

    /**
     * Scheduled job that runs every 10 minutes to check for ended billing periods.
     * 10-minute interval ensures we catch hourly billing cycles very promptly.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void monitorBillingPeriodsAndGenerateInvoices() {
        log.info("🔍 Starting billing period monitoring job...");
        
        try {
            // Get all organization IDs that have activity in the system
            List<Long> organizationIds = organizationRepository.findAllOrganizationIds();
            
            if (organizationIds.isEmpty()) {
                log.info("No organizations found with activity. Skipping billing period check.");
                return;
            }
            
            log.info("Found {} organization(s) to process", organizationIds.size());
            
            int totalInvoicesGenerated = 0;
            
            // Process each organization independently
            for (Long organizationId : organizationIds) {
                try {
                    int invoicesGenerated = processOrganization(organizationId);
                    totalInvoicesGenerated += invoicesGenerated;
                } catch (Exception e) {
                    log.error("❌ Error processing organization {}: {}", 
                            organizationId, e.getMessage(), e);
                    // Continue with other organizations even if one fails
                }
            }
            
            log.info("✅ Billing period monitoring completed. Generated {} invoice(s) across all organizations.", 
                    totalInvoicesGenerated);
            
        } catch (Exception e) {
            log.error("❌ Fatal error in billing period monitoring: {}", e.getMessage(), e);
        }
    }

    /**
     * Process all subscriptions for a single organization.
     * 
     * @param organizationId Organization ID to process
     * @return Number of invoices generated
     */
    private int processOrganization(Long organizationId) {
        log.info("📊 Processing organization {}...", organizationId);
        
        int invoicesGenerated = 0;
        
        try {
            // Generate service JWT token for this organization
            String serviceToken = jwtTokenGenerator.generateServiceToken(organizationId);
            
            // Set tenant context for service calls (will be cleared after processing)
            TenantContext.setOrganizationId(organizationId);
            TenantContext.setJwtToken(serviceToken);
            
            // Fetch all active subscriptions for this organization
            List<SubscriptionResponse> subscriptions = subscriptionClient
                    .getAllActiveSubscriptions(organizationId, serviceToken);
            
            if (subscriptions.isEmpty()) {
                log.info("No active subscriptions for organization {}", organizationId);
                return 0;
            }
            
            log.info("Found {} active subscription(s) for organization {}", 
                    subscriptions.size(), organizationId);
            
            // Check each subscription for ended billing periods
            for (SubscriptionResponse subscription : subscriptions) {
                try {
                    if (shouldGenerateInvoice(subscription, organizationId, serviceToken)) {
                        generateInvoiceForEndedPeriod(subscription, organizationId, serviceToken);
                        invoicesGenerated++;
                    }
                } catch (Exception e) {
                    log.error("❌ Error processing subscription {}: {}", 
                            subscription.getSubscriptionId(), e.getMessage(), e);
                    // Continue with other subscriptions even if one fails
                }
            }
            
            if (invoicesGenerated > 0) {
                log.info("✅ Generated {} invoice(s) for organization {}", 
                        invoicesGenerated, organizationId);
            }
            
        } finally {
            // Always clear tenant context after processing
            TenantContext.clear();
        }
        
        return invoicesGenerated;
    }

    /**
     * Check if an invoice should be generated for a subscription.
     * 
     * Conditions:
     * 1. Subscription has billing period end date
     * 2. Current time >= billing period end
     * 3. No invoice already exists for this period
     * 
     * @param subscription Subscription to check
     * @param organizationId Organization ID
     * @param serviceToken Service JWT token
     * @return true if invoice should be generated
     */
    private boolean shouldGenerateInvoice(SubscriptionResponse subscription, 
                                          Long organizationId, 
                                          String serviceToken) {
        
        Long subscriptionId = subscription.getSubscriptionId();
        Instant periodEnd = subscription.getCurrentBillingPeriodEnd();
        Instant periodStart = subscription.getCurrentBillingPeriodStart();
        
        // Check if subscription has billing period information
        if (periodEnd == null) {
            log.debug("Subscription {} has no billing period end date. Skipping.", subscriptionId);
            return false;
        }
        
        if (periodStart == null) {
            log.debug("Subscription {} has no billing period start date. Skipping.", subscriptionId);
            return false;
        }
        
        try {
            Instant now = Instant.now();
            
            // Check if billing period has ended
            if (now.isBefore(periodEnd)) {
                log.debug("Subscription {} billing period has not ended yet. " +
                         "End: {}, Now: {}", subscriptionId, periodEnd, now);
                return false;
            }
            
            log.info("🔔 Subscription {} billing period has ENDED! " +
                    "Start: {}, End: {}, Now: {}", 
                    subscriptionId, periodStart, periodEnd, now);
            
            // Check if invoice already exists for this period
            boolean invoiceExists = invoiceRepository.existsForPeriod(
                    organizationId, 
                    subscriptionId, 
                    periodStart, 
                    periodEnd
            );
            
            if (invoiceExists) {
                log.debug("Invoice already exists for subscription {} period {} to {}. Skipping.",
                        subscriptionId, periodStart, periodEnd);
                return false;
            }
            
            log.info("✅ Should generate invoice for subscription {} " +
                    "(period {} to {})", subscriptionId, periodStart, periodEnd);
            return true;
            
        } catch (Exception e) {
            log.error("Error checking subscription {} billing period: {}", 
                    subscriptionId, e.getMessage());
            return false;
        }
    }

    /**
     * Generate invoice for a subscription with an ended billing period.
     * 
     * @param subscription Subscription to generate invoice for
     * @param organizationId Organization ID
     * @param serviceToken Service JWT token
     */
    private void generateInvoiceForEndedPeriod(SubscriptionResponse subscription, 
                                               Long organizationId, 
                                               String serviceToken) {
        
        Long subscriptionId = subscription.getSubscriptionId();
        Long customerId = subscription.getCustomerId();
        Long ratePlanId = subscription.getRatePlanId();
        
        try {
            // Get billing period dates (now as Instant)
            Instant periodStart = subscription.getCurrentBillingPeriodStart();
            Instant periodEnd = subscription.getCurrentBillingPeriodEnd();
            
            log.info("💰 Generating invoice for subscription {} " +
                    "(customer: {}, period: {} to {})", 
                    subscriptionId, customerId, periodStart, periodEnd);
            
            // Set tenant context for metering calculation
            TenantContext.setOrganizationId(organizationId);
            TenantContext.setJwtToken(serviceToken);
            
            // Step 1: Calculate metering for the completed period
            MeterRequest meterRequest = MeterRequest.builder()
                    .subscriptionId(subscriptionId)
                    .from(periodStart)
                    .to(periodEnd)
                    .build();
            
            log.debug("Calculating metering for subscription {} from {} to {}", 
                    subscriptionId, periodStart, periodEnd);
            
            MeterResponse meterResponse = meterService.estimate(meterRequest);
            
            log.info("📊 Metering calculated for subscription {}: " +
                    "{} events, total amount: ${}", 
                    subscriptionId, meterResponse.getEventCount(), meterResponse.getTotal());
            
            // Step 2: Create invoice from metering results
            Invoice invoice = invoiceService.createInvoiceFromMeterResponse(
                    meterResponse,
                    organizationId,
                    customerId,
                    subscriptionId,
                    ratePlanId,
                    periodStart,
                    periodEnd
            );
            
            log.info("🎉 Invoice {} created automatically for subscription {} " +
                    "(total: ${}, line items: {})", 
                    invoice.getInvoiceNumber(),
                    subscriptionId,
                    invoice.getTotalAmount(),
                    invoice.getLineItems().size());
            
            // Step 3: QuickBooks webhook is automatically triggered by InvoiceService.createInvoiceFromMeterResponse()
            // No additional action needed here
            
        } catch (Exception e) {
            log.error("❌ Failed to generate invoice for subscription {}: {}", 
                    subscriptionId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate invoice for subscription " + subscriptionId, e);
        }
    }

    // parseDate method removed - no longer needed since subscription service returns Instant directly
}
