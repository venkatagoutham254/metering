package aforo.metering.controller;

import aforo.metering.dto.MeterRequest;
import aforo.metering.dto.MeterResponse;
import aforo.metering.dto.TriggerMeterRequest;
import aforo.metering.service.MeterService;
import aforo.metering.service.AutoMeteringService;
import aforo.metering.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/meter", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Metering", description = "Get usage and cost in single call")
public class MeterController {

    private final MeterService meterService;
    private final AutoMeteringService autoMeteringService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Estimate cost for a time range",
            description = "Returns line-item breakdown and total cost based on usage in the given period",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success",
                            content = @Content(schema = @Schema(implementation = MeterResponse.class)))
            })
    public ResponseEntity<MeterResponse> estimate(@Valid @RequestBody MeterRequest request) {
        return ResponseEntity.ok(meterService.estimate(request));
    }
    
    @PostMapping("/trigger")
    @Operation(
            summary = "Trigger metering after ingestion",
            description = "Called by ingestion service after successful event processing")
    public ResponseEntity<Map<String, String>> triggerMetering(@RequestBody TriggerMeterRequest request) {
        Long subscriptionId = request.getSubscriptionId();

        if (subscriptionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "subscriptionId is required"));
        }

        Instant from = request.getFrom() != null ? request.getFrom() : Instant.now().minusSeconds(3600);
        Instant to = request.getTo() != null ? request.getTo() : Instant.now();

        // Capture organization ID and JWT token from current context before async call
        Long organizationId = TenantContext.getOrganizationId();
        String jwtToken = TenantContext.getJwtToken();

        // Trigger async processing with organization ID and JWT token
        autoMeteringService.processMeteringForSubscription(organizationId, jwtToken, subscriptionId, from, to);

        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Metering calculation triggered"
        ));
    }
    
    @PostMapping("/batch")
    @Operation(
            summary = "Process batch metering",
            description = "Process metering for multiple rate plans")
    public ResponseEntity<Map<String, Object>> processBatch(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<Long> ratePlanIds = (List<Long>) payload.get("ratePlanIds");
        String fromStr = (String) payload.get("from");
        String toStr = (String) payload.get("to");
        
        if (ratePlanIds == null || ratePlanIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ratePlanIds list is required"));
        }
        
        Instant from = fromStr != null ? Instant.parse(fromStr) : Instant.now().minusSeconds(86400);
        Instant to = toStr != null ? Instant.parse(toStr) : Instant.now();
        
        autoMeteringService.processBatchMetering(ratePlanIds, from, to);
        
        return ResponseEntity.ok(Map.of(
                "status", "processing",
                "message", String.format("Processing metering for %d rate plans", ratePlanIds.size())
        ));
    }
}
