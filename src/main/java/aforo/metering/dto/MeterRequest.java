package aforo.metering.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Simplified request object mirroring EstimateRequest pattern.
 * Only provides the time window and optional identifiers needed
 * to fetch usage from ingestion_event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeterRequest {
    private Instant from;        // inclusive
    private Instant to;          // exclusive

    // Filters – provide at least ratePlanId to price correctly
    private Long subscriptionId;
    private Long productId;
    private Long ratePlanId;
    private Long billableMetricId;
}
