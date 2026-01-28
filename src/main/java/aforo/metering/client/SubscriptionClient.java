package aforo.metering.client;

import aforo.metering.client.dto.SubscriptionResponse;
import aforo.metering.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.List;

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
            @Value("${aforo.subscription-service.base-url:http://subscription.dev.aforo.space:8084}") String subscriptionServiceBaseUrl) {
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

    /**
     * Fetch all active subscriptions for an organization.
     * Used by scheduled jobs to monitor billing periods.
     * 
     * @param organizationId Organization ID
     * @param jwtToken JWT token for authentication (service token)
     * @return List of active subscriptions
     */
    public List<SubscriptionResponse> getAllActiveSubscriptions(Long organizationId, String jwtToken) {
        try {
            log.debug("Fetching all active subscriptions for organization {} from {}", 
                    organizationId, subscriptionServiceBaseUrl);

            List<SubscriptionResponse> subscriptions = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/subscriptions")
                            .queryParam("organizationId", organizationId)
                            .queryParam("status", "ACTIVE")
                            .build())
                    .header("X-Organization-Id", organizationId.toString())
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<SubscriptionResponse>>() {})
                    .block();

            if (subscriptions == null) {
                log.warn("No subscriptions returned for organization {}", organizationId);
                return Collections.emptyList();
            }

            log.info("Successfully fetched {} active subscription(s) for organization {}", 
                    subscriptions.size(), organizationId);

            return subscriptions;

        } catch (WebClientResponseException e) {
            log.error("Error fetching subscriptions for organization {}: {} - {}", 
                    organizationId, e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching subscriptions for organization {}: {}", 
                    organizationId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
