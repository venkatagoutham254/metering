package aforo.metering.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUsageResponse {
    
    private Long customerId;
    private BigDecimal currentPeriodUsage;
    private BigDecimal previousPeriodUsage;
    private Double usageChangePercentage;
    private String period; // e.g., "Last 30 days"
}
