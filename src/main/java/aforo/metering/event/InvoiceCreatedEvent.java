package aforo.metering.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when a new invoice is created.
 * This event is listened to by the QuickBooks integration service
 * to automatically sync the invoice to QuickBooks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCreatedEvent {

    /**
     * The invoice ID (primary key).
     */
    private Long invoiceId;

    /**
     * Organization ID for tenant isolation.
     */
    private Long organizationId;

    /**
     * Customer ID the invoice belongs to.
     */
    private Long customerId;

    /**
     * Subscription ID if applicable.
     */
    private Long subscriptionId;

    /**
     * Rate plan ID used for this invoice.
     */
    private Long ratePlanId;

    /**
     * Invoice number (business key).
     */
    private String invoiceNumber;

    /**
     * Total invoice amount.
     */
    private BigDecimal totalAmount;

    /**
     * Billing period start.
     */
    private Instant billingPeriodStart;

    /**
     * Billing period end.
     */
    private Instant billingPeriodEnd;

    /**
     * When the invoice was created.
     */
    private Instant createdAt;
}
