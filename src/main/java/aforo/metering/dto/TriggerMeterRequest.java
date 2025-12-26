package aforo.metering.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "subscriptionId is required")
    @Schema(description = "Subscription ID to meter", example = "9101")
    private Long subscriptionId;

    @Schema(description = "Start of window (inclusive). If not provided, uses subscription's current billing period start.", 
            example = "2025-10-01T00:00:00Z")
    private Instant from;

    @Schema(description = "End of window (exclusive). If not provided, uses current time.", 
            example = "2025-11-01T00:00:00Z")
    private Instant to;
}
