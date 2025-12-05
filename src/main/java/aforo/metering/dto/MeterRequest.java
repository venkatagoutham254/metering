package aforo.metering.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

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
    @Schema(description = "Start of window (inclusive)", example = "2025-11-03T05:35:42.552Z")
    private Instant from;        // inclusive

    @Schema(description = "End of window (exclusive)", example = "2025-11-28T05:35:42.552Z")
    private Instant to;          // exclusive

    // Filters – provide at least ratePlanId to price correctly
    @Schema(description = "Subscription ID to meter for the given period", example = "1")
    private Long subscriptionId;

    @Schema(hidden = true)
    private Long productId;

    @Schema(hidden = true)
    private Long ratePlanId;

    @Schema(hidden = true)
    private Long billableMetricId;
}
