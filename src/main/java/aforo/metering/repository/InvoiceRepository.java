package aforo.metering.repository;

import aforo.metering.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Invoice entities.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Find invoice by ID with line items eagerly loaded.
     */
    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.id = :id")
    Optional<Invoice> findByIdWithLineItems(@Param("id") Long id);

    /**
     * Find invoice by invoice number with line items eagerly loaded.
     */
    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.invoiceNumber = :invoiceNumber")
    Optional<Invoice> findByInvoiceNumberWithLineItems(@Param("invoiceNumber") String invoiceNumber);
    
    /**
     * Find invoice by invoice number.
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find all invoices for an organization with line items eagerly loaded.
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.organizationId = :orgId ORDER BY i.createdAt DESC")
    List<Invoice> findByOrganizationIdWithLineItems(@Param("orgId") Long organizationId);
    
    /**
     * Find all invoices for an organization.
     */
    List<Invoice> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    /**
     * Find all invoices for a customer with line items eagerly loaded.
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.organizationId = :orgId AND i.customerId = :customerId ORDER BY i.createdAt DESC")
    List<Invoice> findByOrganizationIdAndCustomerIdWithLineItems(@Param("orgId") Long organizationId, @Param("customerId") Long customerId);
    
    /**
     * Find all invoices for a customer.
     */
    List<Invoice> findByOrganizationIdAndCustomerIdOrderByCreatedAtDesc(Long organizationId, Long customerId);

    /**
     * Find all invoices for a subscription with line items eagerly loaded.
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.organizationId = :orgId AND i.subscriptionId = :subscriptionId ORDER BY i.createdAt DESC")
    List<Invoice> findByOrganizationIdAndSubscriptionIdWithLineItems(@Param("orgId") Long organizationId, @Param("subscriptionId") Long subscriptionId);
    
    /**
     * Find all invoices for a subscription.
     */
    List<Invoice> findByOrganizationIdAndSubscriptionIdOrderByCreatedAtDesc(Long organizationId, Long subscriptionId);

    /**
     * Find invoices by status with line items eagerly loaded.
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.organizationId = :orgId AND i.status = :status ORDER BY i.createdAt DESC")
    List<Invoice> findByOrganizationIdAndStatusWithLineItems(@Param("orgId") Long organizationId, @Param("status") Invoice.InvoiceStatus status);
    
    /**
     * Find invoices by status.
     */
    List<Invoice> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, Invoice.InvoiceStatus status);

    /**
     * Find invoices in a date range with line items eagerly loaded.
     */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.organizationId = :orgId " +
           "AND i.billingPeriodStart >= :from AND i.billingPeriodEnd <= :to " +
           "ORDER BY i.createdAt DESC")
    List<Invoice> findInvoicesByDateRange(
            @Param("orgId") Long organizationId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * Check if an invoice exists for a specific billing period and subscription.
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END " +
           "FROM Invoice i WHERE i.organizationId = :orgId " +
           "AND i.subscriptionId = :subscriptionId " +
           "AND i.billingPeriodStart = :periodStart " +
           "AND i.billingPeriodEnd = :periodEnd")
    boolean existsForPeriod(
            @Param("orgId") Long organizationId,
            @Param("subscriptionId") Long subscriptionId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );
}
