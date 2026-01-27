package aforo.metering.dto;

import aforo.metering.entity.Invoice;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceListResponse {
    private List<Invoice> invoices;
    private int numberOfInvoices;
}
