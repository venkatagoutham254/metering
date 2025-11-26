package aforo.metering.service;

import aforo.metering.dto.MeterResponse;
import aforo.metering.entity.Invoice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for invoice operations.
 * Handles invoice creation from metering results and invoice retrieval.
 */
public interface InvoiceService {

    /**
     * Create an invoice from metering results.
     *
     * @param meterResponse Metering calculation results with breakdown
     * @param organizationId Organization ID for the invoice
     * @param customerId Customer ID for the invoice
     * @param subscriptionId Subscription ID (optional)
     * @param ratePlanId Rate plan ID used for calculations
     * @param billingPeriodStart Start of the billing period
     * @param billingPeriodEnd End of the billing period
     * @return Created invoice entity
     */
    Invoice createInvoiceFromMeterResponse(
            MeterResponse meterResponse,
            Long organizationId,
            Long customerId,
            Long subscriptionId,
            Long ratePlanId,
            Instant billingPeriodStart,
            Instant billingPeriodEnd
    );

    /**
     * Get invoice by ID.
     *
     * @param invoiceId Invoice ID
     * @return Invoice if found
     */
    Optional<Invoice> getInvoiceById(Long invoiceId);

    /**
     * Get invoice by invoice number.
     *
     * @param invoiceNumber Invoice number
     * @return Invoice if found
     */
    Optional<Invoice> getInvoiceByNumber(String invoiceNumber);

    /**
     * Get all invoices for an organization.
     *
     * @param organizationId Organization ID
     * @return List of invoices
     */
    List<Invoice> getInvoicesByOrganization(Long organizationId);

    /**
     * Get all invoices for a customer.
     *
     * @param organizationId Organization ID
     * @param customerId Customer ID
     * @return List of invoices
     */
    List<Invoice> getInvoicesByCustomer(Long organizationId, Long customerId);

    /**
     * Get all invoices for a subscription.
     *
     * @param organizationId Organization ID
     * @param subscriptionId Subscription ID
     * @return List of invoices
     */
    List<Invoice> getInvoicesBySubscription(Long organizationId, Long subscriptionId);

    /**
     * Get invoices by status.
     *
     * @param organizationId Organization ID
     * @param status Invoice status
     * @return List of invoices
     */
    List<Invoice> getInvoicesByStatus(Long organizationId, Invoice.InvoiceStatus status);

    /**
     * Get invoices in a date range.
     *
     * @param organizationId Organization ID
     * @param from Start date
     * @param to End date
     * @return List of invoices
     */
    List<Invoice> getInvoicesByDateRange(Long organizationId, Instant from, Instant to);

    /**
     * Update invoice status.
     *
     * @param invoiceId Invoice ID
     * @param status New status
     * @return Updated invoice
     */
    Invoice updateInvoiceStatus(Long invoiceId, Invoice.InvoiceStatus status);

    /**
     * Check if an invoice already exists for a given period and subscription.
     *
     * @param organizationId Organization ID
     * @param subscriptionId Subscription ID
     * @param periodStart Billing period start
     * @param periodEnd Billing period end
     * @return true if invoice exists
     */
    boolean invoiceExistsForPeriod(Long organizationId, Long subscriptionId, Instant periodStart, Instant periodEnd);
}
