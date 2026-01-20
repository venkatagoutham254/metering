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
        private BigDecimal overageUnitRate;
        private Integer graceBuffer;
    }
    
    @Data
    public static class TierDTO {
        @JsonAlias("startRange")
        private Integer minUnits;
        @JsonAlias("endRange")
        private Integer maxUnits;
        @JsonAlias({"perUnitAmount", "unitPrice"})
        private BigDecimal pricePerUnit;
    }
    
    @Data
    public static class VolumePricingDTO {
        private List<VolumeTierDTO> tiers;
        private BigDecimal overageUnitRate;
        private Integer graceBuffer;
    }
    
    @Data
    public static class VolumeTierDTO {
        @JsonAlias({"startRange", "usageStart"})
        private Integer minUnits;
        @JsonAlias({"endRange", "usageEnd"})
        private Integer maxUnits;
        @JsonAlias({"perUnitAmount", "unitPrice"})
        private BigDecimal pricePerUnit;
    }
    
    @Data
    public static class UsageBasedPricingDTO {
        @JsonAlias("perUnitAmount")
        private BigDecimal pricePerUnit;
    }
    
    @Data
    public static class StairStepPricingDTO {
        @JsonAlias("tiers")
        private List<StepDTO> steps;
        private BigDecimal overageUnitRate;
        private Integer graceBuffer;
    }
    
    @Data
    public static class StepDTO {
        @JsonAlias("usageStart")
        private Integer usageThresholdStart;
        @JsonAlias("usageEnd")
        private Integer usageThresholdEnd;
        @JsonAlias("flatCost")
        private BigDecimal monthlyCharge;
    }
    
    @Data
    public static class SetupFeeDTO {
        @JsonAlias("setupFee")
        private BigDecimal amount;
        private Integer applicationTiming;
        private String invoiceDescription;
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
        @JsonAlias("minimumCharge")
        private BigDecimal minimumAmount;
        private Integer minimumUsage;
    }
}
