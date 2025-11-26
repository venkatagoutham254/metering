package aforo.metering.client;

import aforo.metering.client.dto.RatePlanDTO;
import aforo.metering.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.List;

/**
 * Client for interacting with the Product Rate Plan service.
 * Fetches rate plan configurations including pricing models and extras.
 */
@Component
@RequiredArgsConstructor
public class RatePlanClient {
    
    private static final Logger logger = LoggerFactory.getLogger(RatePlanClient.class);
    
    private final WebClient ratePlanWebClient;
    
    /**
     * Fetch a rate plan by ID.
     * Returns null if not found or on error.
     */
    public RatePlanDTO fetchRatePlan(Long ratePlanId) {
        Long orgId = TenantContext.requireOrganizationId();
        String jwtToken = TenantContext.getJwtToken();
        try {
            return ratePlanWebClient.get()
                    .uri("/api/rateplans/{id}", ratePlanId)
                    .header("X-Organization-Id", String.valueOf(orgId))
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(RatePlanDTO.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Rate plan not found: {}", ratePlanId);
            return null;
        } catch (WebClientResponseException e) {
            logger.error("Error fetching rate plan {}: {} {}", ratePlanId, e.getRawStatusCode(), e.getStatusText());
            // Fallback: some deployments fail on /api/rateplans/{id} with 5xx; fetch list and filter locally
            if (e.getStatusCode().is5xxServerError()) {
                try {
                    List<RatePlanDTO> all = ratePlanWebClient.get()
                            .uri("/api/rateplans")
                            .header("X-Organization-Id", String.valueOf(orgId))
                            .header("Authorization", "Bearer " + jwtToken)
                            .retrieve()
                            .bodyToFlux(RatePlanDTO.class)
                            .collectList()
                            .block();
                    if (all != null) {
                        for (RatePlanDTO rp : all) {
                            if (rp != null && ratePlanId.equals(rp.getRatePlanId())) {
                                return rp;
                            }
                        }
                    }
                } catch (Exception ex2) {
                    logger.error("Fallback '/api/rateplans' failed: {}", ex2.getMessage());
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Error fetching rate plan {}: {}", ratePlanId, e.getMessage());
            return null;
        }
    }
}
