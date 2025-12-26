package aforo.metering.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeterResponse {

    private String modelType;             // e.g. "FLATFEE", "USAGE_BASED" etc.
    private Integer eventCount;           // number of events processed
    private List<LineItem> breakdown;     // detailed line items
    private BigDecimal total;             // final charge

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private String label;
        private String calculation;
        private BigDecimal amount;       // positive = charge, negative = credit/discount
    }
}
