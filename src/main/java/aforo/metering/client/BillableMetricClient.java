package aforo.metering.client;

import aforo.metering.client.dto.BillableMetricResponse;
import aforo.metering.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Client for interacting with the Billable Metrics service.
 * Propagates tenant context (organization ID and JWT token) with all requests.
 */
@Component
@RequiredArgsConstructor
public class BillableMetricClient {
    
    private static final Logger logger = LoggerFactory.getLogger(BillableMetricClient.class);
    
    private final WebClient billableMetricWebClient;
    
    /**
     * Fetch a billable metric by ID.
     * Returns null if not found or on error.
     */
    public BillableMetricResponse fetchMetric(Long metricId) {
        try {
            Long orgId = TenantContext.requireOrganizationId();
            String jwtToken = TenantContext.getJwtToken();
            
            return billableMetricWebClient.get()
                    .uri("/api/billable-metrics/{id}", metricId)
                    .header("X-Organization-Id", String.valueOf(orgId))
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(BillableMetricResponse.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Billable metric not found: {}", metricId);
            return null;
        } catch (Exception e) {
            logger.error("Error fetching billable metric {}: {}", metricId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a metric exists and is active.
     */
    public boolean isMetricActive(Long metricId) {
        BillableMetricResponse metric = fetchMetric(metricId);
        return metric != null && "ACTIVE".equalsIgnoreCase(metric.getStatus());
    }
}
