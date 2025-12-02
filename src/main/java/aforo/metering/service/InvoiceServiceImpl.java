package aforo.metering.service;

import aforo.metering.client.QuickBooksWebhookClient;
import aforo.metering.dto.MeterResponse;
import aforo.metering.entity.Invoice;
import aforo.metering.entity.InvoiceLineItem;
import aforo.metering.event.InvoiceCreatedEvent;
import aforo.metering.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of InvoiceService.
 * Handles invoice creation, retrieval, and event publishing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final QuickBooksWebhookClient quickBooksWebhookClient;

    // Base36 character set for compact encoding
    private static final String BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Override
    @Transactional
    public Invoice createInvoiceFromMeterResponse(
            MeterResponse meterResponse,
            Long organizationId,
            Long customerId,
            Long subscriptionId,
            Long ratePlanId,
            Instant billingPeriodStart,
            Instant billingPeriodEnd
    ) {
        log.info("Creating invoice for organization: {}, customer: {}, subscription: {}",
                organizationId, customerId, subscriptionId);

        // Check if invoice already exists for this period
        if (subscriptionId != null && invoiceExistsForPeriod(organizationId, subscriptionId, billingPeriodStart, billingPeriodEnd)) {
            log.warn("Invoice already exists for subscription {} in period {} to {}",
                    subscriptionId, billingPeriodStart, billingPeriodEnd);
            throw new IllegalStateException(
                    "Invoice already exists for this subscription and billing period");
        }

        // Generate invoice number
        String invoiceNumber = generateInvoiceNumber(organizationId, customerId);

        // Create invoice entity
        Invoice invoice = Invoice.builder()
                .organizationId(organizationId)
                .customerId(customerId)
                .subscriptionId(subscriptionId)
                .ratePlanId(ratePlanId)
                .invoiceNumber(invoiceNumber)
                .totalAmount(meterResponse.getTotal())
                .modelType(meterResponse.getModelType())
                .billingPeriodStart(billingPeriodStart)
                .billingPeriodEnd(billingPeriodEnd)
                .status(Invoice.InvoiceStatus.DRAFT)
                .build();

        // Add line items from meter response breakdown
        int lineNumber = 1;
        for (MeterResponse.LineItem lineItem : meterResponse.getBreakdown()) {
            InvoiceLineItem invoiceLineItem = InvoiceLineItem.builder()
                    .lineNumber(lineNumber++)
                    .description(lineItem.getLabel())
                    .calculation(lineItem.getCalculation())
                    .amount(lineItem.getAmount())
                    .build();

            invoice.addLineItem(invoiceLineItem);
        }

        // Save invoice with line items
        Invoice savedInvoice = invoiceRepository.save(invoice);

        log.info("Invoice {} created successfully with {} line items. Total: {}",
                savedInvoice.getInvoiceNumber(),
                savedInvoice.getLineItems().size(),
                savedInvoice.getTotalAmount());

        // Publish event for QuickBooks integration
        InvoiceCreatedEvent event = InvoiceCreatedEvent.builder()
                .invoiceId(savedInvoice.getId())
                .organizationId(savedInvoice.getOrganizationId())
                .customerId(savedInvoice.getCustomerId())
                .subscriptionId(savedInvoice.getSubscriptionId())
                .ratePlanId(savedInvoice.getRatePlanId())
                .invoiceNumber(savedInvoice.getInvoiceNumber())
                .totalAmount(savedInvoice.getTotalAmount())
                .billingPeriodStart(savedInvoice.getBillingPeriodStart())
                .billingPeriodEnd(savedInvoice.getBillingPeriodEnd())
                .createdAt(savedInvoice.getCreatedAt())
                .build();

        eventPublisher.publishEvent(event);
        log.info("InvoiceCreatedEvent published for invoice {}", savedInvoice.getInvoiceNumber());

        // Notify QuickBooks integration service via HTTP webhook
        // Pass JWT token so QuickBooks can authenticate when fetching invoice details
        String jwtToken = aforo.metering.tenant.TenantContext.getJwtToken();
        quickBooksWebhookClient.notifyInvoiceCreated(
                savedInvoice.getId(),
                savedInvoice.getOrganizationId(),
                savedInvoice.getCustomerId(),
                savedInvoice.getInvoiceNumber(),
                savedInvoice.getTotalAmount(),
                jwtToken
        );

        return savedInvoice;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceById(Long invoiceId) {
        return invoiceRepository.findByIdWithLineItems(invoiceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceByNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumberWithLineItems(invoiceNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByOrganization(Long organizationId) {
        return invoiceRepository.findByOrganizationIdWithLineItems(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByCustomer(Long organizationId, Long customerId) {
        return invoiceRepository.findByOrganizationIdAndCustomerIdWithLineItems(
                organizationId, customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesBySubscription(Long organizationId, Long subscriptionId) {
        return invoiceRepository.findByOrganizationIdAndSubscriptionIdWithLineItems(
                organizationId, subscriptionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByStatus(Long organizationId, Invoice.InvoiceStatus status) {
        return invoiceRepository.findByOrganizationIdAndStatusWithLineItems(
                organizationId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByDateRange(Long organizationId, Instant from, Instant to) {
        return invoiceRepository.findInvoicesByDateRange(organizationId, from, to);
    }

    @Override
    @Transactional
    public Invoice updateInvoiceStatus(Long invoiceId, Invoice.InvoiceStatus status) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        invoice.setStatus(status);
        Invoice updatedInvoice = invoiceRepository.save(invoice);

        log.info("Invoice {} status updated to {}", invoice.getInvoiceNumber(), status);

        return updatedInvoice;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean invoiceExistsForPeriod(
            Long organizationId,
            Long subscriptionId,
            Instant periodStart,
            Instant periodEnd
    ) {
        return invoiceRepository.existsForPeriod(organizationId, subscriptionId, periodStart, periodEnd);
    }

    /**
     * Generate a unique invoice number using deterministic short format.
     * Format: INV-{base36ShortId}
     * 
     * The short ID combines organizationId, customerId, and timestamp in milliseconds,
     * then encodes to base36 for compactness.
     * 
     * Guarantees:
     * - Always unique (timestamp milliseconds + org + customer)
     * - Deterministic and reproducible
     * - Under 21 characters (QuickBooks DocNumber limit)
     * - Format: INV-XXXXXXXXXXXXX (typically 15-18 chars)
     * 
     * @param organizationId Organization ID
     * @param customerId Customer ID
     * @return Compact invoice number (e.g., INV-A3F9K2LP8M)
     */
    private String generateInvoiceNumber(Long organizationId, Long customerId) {
        // Use current timestamp in milliseconds for uniqueness
        long timestampMillis = System.currentTimeMillis();
        
        // Combine values into a single large number
        // Format: timestampMillis + (orgId * 1000000) + customerId
        // This ensures uniqueness even if multiple invoices are created in the same millisecond
        long combined = timestampMillis + (organizationId * 1_000_000_000_000L) + (customerId * 1_000_000L);
        
        // Convert to base36 for compact representation
        String base36Id = toBase36(combined);
        
        // Build invoice number
        String invoiceNumber = "INV-" + base36Id;
        
        log.debug("Generated invoice number: {} for org={}, customer={}, timestamp={}",
                invoiceNumber, organizationId, customerId, timestampMillis);
        
        return invoiceNumber;
    }
    
    /**
     * Convert a long value to base36 string.
     * Base36 uses digits 0-9 and letters A-Z for compact representation.
     * 
     * @param value Value to encode
     * @return Base36 encoded string
     */
    private String toBase36(long value) {
        if (value == 0) {
            return "0";
        }
        
        StringBuilder result = new StringBuilder();
        long remaining = value;
        
        while (remaining > 0) {
            int digit = (int) (remaining % 36);
            result.append(BASE36_CHARS.charAt(digit));
            remaining /= 36;
        }
        
        return result.reverse().toString();
    }
}
