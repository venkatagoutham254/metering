package aforo.metering.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for calling QuickBooks Integration service webhooks.
 */
@Component
@Slf4j
public class QuickBooksWebhookClient {

    private final WebClient webClient;
    private final String quickbooksBaseUrl;

    public QuickBooksWebhookClient(
            WebClient.Builder webClientBuilder,
            @Value("${aforo.quickbooks-integration.base-url:http://localhost:8095}") String quickbooksBaseUrl) {
        this.quickbooksBaseUrl = quickbooksBaseUrl;
        this.webClient = webClientBuilder.baseUrl(quickbooksBaseUrl).build();
        log.info("🔗 QuickBooks Integration Client initialized with base URL: {}", quickbooksBaseUrl);
    }

    /**
     * Notify QuickBooks integration service that an invoice was created.
     */
    public void notifyInvoiceCreated(Long invoiceId, Long organizationId, Long customerId, String invoiceNumber, BigDecimal totalAmount, String jwtToken) {
        try {
            log.info("📤 Notifying QuickBooks integration about invoice {} for organization {}", 
                    invoiceNumber, organizationId);

            Map<String, Object> payload = new HashMap<>();
            payload.put("invoiceId", invoiceId);
            payload.put("organizationId", organizationId);
            payload.put("customerId", customerId);
            payload.put("invoiceNumber", invoiceNumber);
            payload.put("totalAmount", totalAmount);
            payload.put("jwtToken", jwtToken);

            webClient.post()
                    .uri("/api/quickbooks/webhook/invoice-created")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnSuccess(response -> 
                        log.info("✅ QuickBooks webhook called successfully for invoice {}", invoiceNumber))
                    .doOnError(error -> 
                        log.warn("⚠️ Failed to notify QuickBooks integration: {}", error.getMessage()))
                    .onErrorResume(error -> Mono.empty()) // Don't fail invoice creation if QB notification fails
                    .subscribe(); // Fire and forget

        } catch (Exception e) {
            log.warn("⚠️ Error calling QuickBooks webhook for invoice {}: {}", invoiceNumber, e.getMessage());
            // Don't throw - this is a best-effort notification
        }
    }
}
