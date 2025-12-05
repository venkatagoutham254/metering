package aforo.metering.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerMeterRequest {

    @Schema(description = "Start of window (inclusive)", example = "2025-10-01T00:00:00Z")
    private Instant from;

    @Schema(description = "End of window (exclusive)", example = "2025-11-01T00:00:00Z")
    private Instant to;

    @Schema(description = "Subscription ID to meter for the given period", example = "9101")
    private Long subscriptionId;
}
