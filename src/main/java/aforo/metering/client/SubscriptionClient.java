package aforo.metering.client;

import aforo.metering.client.dto.SubscriptionResponse;
import aforo.metering.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Client for fetching subscription details from subscription service.
 */
@Component
@Slf4j
public class SubscriptionClient {

    private final WebClient webClient;
    private final String subscriptionServiceBaseUrl;

    public SubscriptionClient(
            WebClient.Builder webClientBuilder,
            @Value("${aforo.subscription-service.base-url:http://52.90.125.218:8084}") String subscriptionServiceBaseUrl) {
        this.subscriptionServiceBaseUrl = subscriptionServiceBaseUrl;
        this.webClient = webClientBuilder.baseUrl(subscriptionServiceBaseUrl).build();
    }

    /**
     * Fetch subscription details by subscription ID.
     * 
     * @param subscriptionId The subscription ID
     * @return SubscriptionResponse with customer ID and other details
     * @throws RuntimeException if subscription not found or API call fails
     */
    public SubscriptionResponse getSubscription(Long subscriptionId) {
        try {
            String organizationId = TenantContext.requireOrganizationId().toString();
            String jwtToken = TenantContext.getJwtToken();

            log.debug("Fetching subscription {} from {}", subscriptionId, subscriptionServiceBaseUrl);

            SubscriptionResponse response = webClient.get()
                    .uri("/api/subscriptions/{id}", subscriptionId)
                    .header("X-Organization-Id", organizationId)
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(SubscriptionResponse.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Subscription " + subscriptionId + " not found");
            }

            log.debug("Successfully fetched subscription {}, customerId: {}", 
                    subscriptionId, response.getCustomerId());

            return response;

        } catch (WebClientResponseException e) {
            log.error("Error fetching subscription {}: {} - {}", 
                    subscriptionId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch subscription " + subscriptionId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error fetching subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch subscription " + subscriptionId + ": " + e.getMessage(), e);
        }
    }
}
