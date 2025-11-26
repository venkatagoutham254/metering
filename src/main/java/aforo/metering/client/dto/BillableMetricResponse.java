package aforo.metering.client.dto;

import lombok.Data;

@Data
public class BillableMetricResponse {
    private Long id;
    private String name;
    private String uom;
    private String aggregationFunction;  // COUNT, SUM, etc.
    private String aggregationWindow;    // PER_MONTH, etc.
    private String status;               // ACTIVE, INACTIVE
    private Long productId;
    private Long organizationId;
}
