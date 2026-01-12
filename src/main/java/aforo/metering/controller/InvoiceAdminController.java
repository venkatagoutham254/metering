package aforo.metering.controller;

import aforo.metering.client.QuickBooksWebhookClient;
import aforo.metering.entity.Invoice;
import aforo.metering.service.InvoiceService;
import aforo.metering.tenant.TenantContext;
import aforo.metering.util.JwtTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceAdminController {

    private final InvoiceService invoiceService;
    private final QuickBooksWebhookClient quickBooksWebhookClient;
    private final JwtTokenGenerator jwtTokenGenerator;

    /**
     * Manually re-sync a specific invoice to QuickBooks.
     * Use this when an invoice was created but the QuickBooks webhook failed.
     */
    @PostMapping("/{invoiceId}/resync-quickbooks")
    public ResponseEntity<Map<String, Object>> resyncInvoiceToQuickBooks(@PathVariable Long invoiceId) {
        Long organizationId = TenantContext.getOrganizationId();
        log.info("🔄 Manual QuickBooks re-sync requested for invoice {} in organization {}", invoiceId, organizationId);

        Invoice invoice = invoiceService.getInvoiceById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));

        if (!invoice.getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Invoice does not belong to organization " + organizationId);
        }

        // Generate service JWT token for this organization
        String jwtToken = jwtTokenGenerator.generateServiceToken(organizationId);

        // Trigger QuickBooks webhook
        quickBooksWebhookClient.notifyInvoiceCreated(
                invoice.getId(),
                invoice.getOrganizationId(),
                invoice.getCustomerId(),
                invoice.getInvoiceNumber(),
                invoice.getTotalAmount(),
                jwtToken
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "triggered");
        response.put("invoiceId", invoiceId);
        response.put("invoiceNumber", invoice.getInvoiceNumber());
        response.put("message", "QuickBooks sync webhook triggered. Check logs for sync status.");

        log.info("✅ QuickBooks re-sync triggered for invoice {}", invoice.getInvoiceNumber());
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk re-sync all invoices for the current organization to QuickBooks.
     * Use this to recover from QuickBooks service downtime.
     */
    @PostMapping("/resync-all-quickbooks")
    public ResponseEntity<Map<String, Object>> resyncAllInvoicesToQuickBooks() {
        Long organizationId = TenantContext.getOrganizationId();
        log.info("🔄 Bulk QuickBooks re-sync requested for organization {}", organizationId);

        List<Invoice> invoices = invoiceService.getInvoicesByOrganization(organizationId);
        
        if (invoices.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "no_invoices");
            response.put("message", "No invoices found for organization " + organizationId);
            return ResponseEntity.ok(response);
        }

        // Generate service JWT token for this organization
        String jwtToken = jwtTokenGenerator.generateServiceToken(organizationId);

        int successCount = 0;
        for (Invoice invoice : invoices) {
            try {
                quickBooksWebhookClient.notifyInvoiceCreated(
                        invoice.getId(),
                        invoice.getOrganizationId(),
                        invoice.getCustomerId(),
                        invoice.getInvoiceNumber(),
                        invoice.getTotalAmount(),
                        jwtToken
                );
                successCount++;
                log.info("✅ Re-sync triggered for invoice {}", invoice.getInvoiceNumber());
            } catch (Exception e) {
                log.error("❌ Failed to trigger re-sync for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("totalInvoices", invoices.size());
        response.put("triggeredCount", successCount);
        response.put("message", "QuickBooks sync webhooks triggered for " + successCount + " invoices. Check logs for sync status.");

        log.info("✅ Bulk QuickBooks re-sync completed: {}/{} invoices triggered", successCount, invoices.size());
        return ResponseEntity.ok(response);
    }
}
