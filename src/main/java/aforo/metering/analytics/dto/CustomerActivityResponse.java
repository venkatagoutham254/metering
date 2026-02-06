package aforo.metering.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerActivityResponse {
    
    private Long customerId;
    private Instant lastActivityTimestamp;
    private Long daysSinceLastActivity;
    private String activityStatus; // "ACTIVE", "INACTIVE", "DORMANT"
}
