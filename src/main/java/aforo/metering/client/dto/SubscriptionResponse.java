package aforo.metering.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * DTO for subscription response from subscription service.
 * Date fields are Instant to match subscription service's ISO-8601 format.
 * Billing cycle fields added to support automatic metering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private Long subscriptionId;
    private Long taskId;
    private Long productId;
    private Long ratePlanId;
    private String status;
    private Instant createdOn;
    private Instant lastUpdated;
    private String paymentType;
    private String adminNotes;
    private Long customerId;  // This is the field we actually need
    private String productName;
    private String ratePlanName;
    private Long organizationId;
    
    // Billing cycle fields - now Instant to match Subscription Service standardization
    private Instant currentBillingPeriodStart;
    private Instant currentBillingPeriodEnd;
    private Instant nextBillingTimestamp;
    private String billingAnchorInfo;
    private Boolean autoRenew;
    private String billingFrequency;
}
