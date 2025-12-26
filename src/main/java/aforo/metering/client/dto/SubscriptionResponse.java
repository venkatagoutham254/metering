package aforo.metering.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for subscription response from subscription service.
 * Note: Date fields are String to handle external service's custom date format.
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
    private String createdOn;  // Changed to String to handle "20 Nov, 2025 10:08 IST" format
    private String lastUpdated;  // Changed to String to handle "20 Nov, 2025 10:08 IST" format
    private String paymentType;
    private String adminNotes;
    private Long customerId;  // This is the field we actually need
    private String productName;
    private String ratePlanName;
    private Long organizationId;
    
    // Billing cycle fields - kept as String to match Subscription Service format
    private String currentBillingPeriodStart;  // Format: "26 Dec, 2025 11:07 IST"
    private String currentBillingPeriodEnd;    // Format: "26 Jan, 2026 11:07 IST"
    private String nextBillingTimestamp;       // Format: "26 Jan, 2026 11:07 IST"
    private String billingAnchorInfo;
    private Boolean autoRenew;
    private String billingFrequency;
}
