package aforo.metering.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoice entity representing a billing invoice created from metering calculations.
 * Contains the invoice header and associated line items.
 */
@Entity
@Table(name = "invoice",
       indexes = {
           @Index(name = "idx_invoice_org_id", columnList = "organization_id"),
           @Index(name = "idx_invoice_customer_id", columnList = "customer_id"),
           @Index(name = "idx_invoice_subscription_id", columnList = "subscription_id"),
           @Index(name = "idx_invoice_status", columnList = "status"),
           @Index(name = "idx_invoice_number", columnList = "invoice_number")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "rate_plan_id")
    private Long ratePlanId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "model_type", length = 50)
    private String modelType;  // FLATFEE, USAGE_BASED, etc.

    @Column(name = "billing_period_start", nullable = false)
    private Instant billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private Instant billingPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = InvoiceStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Helper method to add a line item to this invoice.
     */
    public void addLineItem(InvoiceLineItem lineItem) {
        lineItems.add(lineItem);
        lineItem.setInvoice(this);
    }

    /**
     * Helper method to remove a line item from this invoice.
     */
    public void removeLineItem(InvoiceLineItem lineItem) {
        lineItems.remove(lineItem);
        lineItem.setInvoice(null);
    }

    public enum InvoiceStatus {
        DRAFT,      // Invoice created but not finalized
        ISSUED,     // Invoice issued to customer
        PAID,       // Invoice paid
        VOID,       // Invoice voided/cancelled
        OVERDUE     // Invoice past due date
    }
}
