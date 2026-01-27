package aforo.metering.controller;

import aforo.metering.dto.InvoiceListResponse;
import aforo.metering.entity.Invoice;
import aforo.metering.service.InvoiceService;
import aforo.metering.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for invoice operations.
 * Provides endpoints for retrieving and managing invoices.
 */
@RestController
@RequestMapping(path = "/api/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Invoices", description = "Invoice management endpoints")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get invoice by ID", description = "Retrieve an invoice by its ID")
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable Long invoiceId) {
        log.info("Getting invoice by ID: {}", invoiceId);
        
        return invoiceService.getInvoiceById(invoiceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{invoiceNumber}")
    @Operation(summary = "Get invoice by number", description = "Retrieve an invoice by its invoice number")
    public ResponseEntity<Invoice> getInvoiceByNumber(@PathVariable String invoiceNumber) {
        log.info("Getting invoice by number: {}", invoiceNumber);
        
        return invoiceService.getInvoiceByNumber(invoiceNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all invoices", description = "Get all invoices for the current organization")
    public ResponseEntity<InvoiceListResponse> getAllInvoices() {
        Long orgId = TenantContext.requireOrganizationId();
        log.info("Getting all invoices for organization: {}", orgId);
        
        List<Invoice> invoices = invoiceService.getInvoicesByOrganization(orgId);
        InvoiceListResponse response = new InvoiceListResponse(invoices, invoices.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get invoices by customer", description = "Get all invoices for a specific customer")
    public ResponseEntity<List<Invoice>> getInvoicesByCustomer(@PathVariable Long customerId) {
        Long orgId = TenantContext.requireOrganizationId();
        log.info("Getting invoices for customer: {} in organization: {}", customerId, orgId);
        
        List<Invoice> invoices = invoiceService.getInvoicesByCustomer(orgId, customerId);
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/subscription/{subscriptionId}")
    @Operation(summary = "Get invoices by subscription", description = "Get all invoices for a specific subscription")
    public ResponseEntity<List<Invoice>> getInvoicesBySubscription(@PathVariable Long subscriptionId) {
        Long orgId = TenantContext.requireOrganizationId();
        log.info("Getting invoices for subscription: {} in organization: {}", subscriptionId, orgId);
        
        List<Invoice> invoices = invoiceService.getInvoicesBySubscription(orgId, subscriptionId);
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get invoices by status", description = "Get all invoices with a specific status")
    public ResponseEntity<List<Invoice>> getInvoicesByStatus(@PathVariable Invoice.InvoiceStatus status) {
        Long orgId = TenantContext.requireOrganizationId();
        log.info("Getting invoices with status: {} for organization: {}", status, orgId);
        
        List<Invoice> invoices = invoiceService.getInvoicesByStatus(orgId, status);
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/date-range")
    @Operation(summary = "Get invoices by date range", description = "Get invoices within a billing period")
    public ResponseEntity<List<Invoice>> getInvoicesByDateRange(
            @RequestParam String from,
            @RequestParam String to
    ) {
        Long orgId = TenantContext.requireOrganizationId();
        Instant fromInstant = Instant.parse(from);
        Instant toInstant = Instant.parse(to);
        
        log.info("Getting invoices for organization: {} from {} to {}", orgId, from, to);
        
        List<Invoice> invoices = invoiceService.getInvoicesByDateRange(orgId, fromInstant, toInstant);
        return ResponseEntity.ok(invoices);
    }

    @PatchMapping("/{invoiceId}/status")
    @Operation(summary = "Update invoice status", description = "Update the status of an invoice")
    public ResponseEntity<Invoice> updateInvoiceStatus(
            @PathVariable Long invoiceId,
            @RequestBody Map<String, String> body
    ) {
        log.info("Updating status for invoice: {}", invoiceId);
        
        String statusStr = body.get("status");
        if (statusStr == null) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Invoice.InvoiceStatus status = Invoice.InvoiceStatus.valueOf(statusStr);
            Invoice updatedInvoice = invoiceService.updateInvoiceStatus(invoiceId, status);
            return ResponseEntity.ok(updatedInvoice);
        } catch (IllegalArgumentException e) {
            log.error("Invalid status value: {}", statusStr);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Get invoice statistics", description = "Get invoice count and totals by status")
    public ResponseEntity<Map<String, Object>> getInvoiceStats() {
        Long orgId = TenantContext.requireOrganizationId();
        log.info("Getting invoice statistics for organization: {}", orgId);
        
        List<Invoice> allInvoices = invoiceService.getInvoicesByOrganization(orgId);
        
        long draftCount = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.DRAFT).count();
        long issuedCount = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.ISSUED).count();
        long paidCount = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.PAID).count();
        long voidCount = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.VOID).count();
        long overdueCount = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.OVERDUE).count();
        
        Map<String, Object> stats = Map.of(
                "total", allInvoices.size(),
                "draft", draftCount,
                "issued", issuedCount,
                "paid", paidCount,
                "void", voidCount,
                "overdue", overdueCount
        );
        
        return ResponseEntity.ok(stats);
    }
}
