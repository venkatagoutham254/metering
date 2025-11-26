package aforo.metering.client.dto;

import lombok.Data;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.time.LocalDate;

@Data
public class RatePlanDTO {
    private Long ratePlanId;
    private String ratePlanName;
    private String description;
    private String billingFrequency;
    private String paymentType;
    private Long billableMetricId;
    private String status;
    
    // Pricing models
    private FlatFeeDTO flatFee;
    private List<TieredPricingDTO> tieredPricings;
    private List<VolumePricingDTO> volumePricings;
    private List<UsageBasedPricingDTO> usageBasedPricings;
    private List<StairStepPricingDTO> stairStepPricings;
    
    // Extras
    private List<SetupFeeDTO> setupFees;
    private List<DiscountDTO> discounts;
    private List<FreemiumDTO> freemiums;
    private List<MinimumCommitmentDTO> minimumCommitments;
    
    @Data
    public static class FlatFeeDTO {
        @JsonAlias("flatFeeAmount")
        private BigDecimal flatFeeAmount;
        @JsonAlias("numberOfApiCalls")
        private Integer includedUnits;
        @JsonAlias("overageUnitRate")
        private BigDecimal overageRate;
    }
    
    @Data
    public static class TieredPricingDTO {
        private List<TierDTO> tiers;
    }
    
    @Data
    public static class TierDTO {
        private Integer minUnits;
        private Integer maxUnits;
        @JsonAlias("perUnitAmount")
        private BigDecimal pricePerUnit;
    }
    
    @Data
    public static class VolumePricingDTO {
        private List<VolumeTierDTO> tiers;
    }
    
    @Data
    public static class VolumeTierDTO {
        private Integer minUnits;
        private Integer maxUnits;
        @JsonAlias("perUnitAmount")
        private BigDecimal pricePerUnit;
    }
    
    @Data
    public static class UsageBasedPricingDTO {
        @JsonAlias("perUnitAmount")
        private BigDecimal pricePerUnit;
    }
    
    @Data
    public static class StairStepPricingDTO {
        private List<StepDTO> steps;
    }
    
    @Data
    public static class StepDTO {
        private Integer usageThresholdStart;
        private Integer usageThresholdEnd;
        private BigDecimal monthlyCharge;
    }
    
    @Data
    public static class SetupFeeDTO {
        private BigDecimal amount;
    }
    
    @Data
    public static class DiscountDTO {
        private String discountType;  // PERCENTAGE or FLAT
        @JsonAlias("percentageDiscount")
        private BigDecimal percentage;
        @JsonAlias("flatDiscountAmount")
        private BigDecimal flatAmount;
        private String eligibility;
        private LocalDate startDate;
        private LocalDate endDate;
    }
    
    @Data
    public static class FreemiumDTO {
        private Integer freeUnits;
    }
    
    @Data
    public static class MinimumCommitmentDTO {
        private BigDecimal minimumAmount;
    }
}
