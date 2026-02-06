package aforo.metering.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageTrendsResponse {
    
    private List<UsageTrend> trends;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageTrend {
        private Long customerId;
        private String customerName; // Optional, can be populated later
        private Double currentUsage;
        private Double previousUsage;
        private Double changePercentage;
        private String trendDirection; // "UP", "DOWN", "STABLE"
    }
}
