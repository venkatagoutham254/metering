package aforo.metering.analytics.controller;

import aforo.metering.analytics.dto.*;
import aforo.metering.analytics.service.MeteringAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/metering/analytics")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MeteringAnalyticsController {

    private final MeteringAnalyticsService analyticsService;

    /**
     * API 1: Get Customer Usage
     * GET /api/metering/analytics/customer-usage
     */
    @GetMapping("/customer-usage")
    public ResponseEntity<CustomerUsageResponse> getCustomerUsage(
            @RequestHeader("X-Organization-Id") Long organizationId,
            @RequestParam Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        
        log.info("GET /customer-usage - org: {}, customer: {}", organizationId, customerId);
        
        if (startDate == null) {
            startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if (endDate == null) {
            endDate = Instant.now();
        }

        CustomerUsageResponse response = analyticsService.getCustomerUsage(organizationId, customerId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    /**
     * API 2: Get Usage Trends
     * GET /api/metering/analytics/usage-trends
     */
    @GetMapping("/usage-trends")
    public ResponseEntity<UsageTrendsResponse> getUsageTrends(
            @RequestHeader("X-Organization-Id") Long organizationId,
            @RequestParam List<Long> customerIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        
        log.info("GET /usage-trends - org: {}, {} customers", organizationId, customerIds.size());
        
        if (startDate == null) {
            startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if (endDate == null) {
            endDate = Instant.now();
        }

        UsageTrendsResponse response = analyticsService.getUsageTrends(organizationId, customerIds, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    /**
     * API 3: Get Customer Activity
     * GET /api/metering/analytics/customer-activity/{customerId}
     */
    @GetMapping("/customer-activity/{customerId}")
    public ResponseEntity<CustomerActivityResponse> getCustomerActivity(
            @PathVariable Long customerId,
            @RequestHeader("X-Organization-Id") Long organizationId) {
        
        log.info("GET /customer-activity/{} - org: {}", customerId, organizationId);
        
        CustomerActivityResponse response = analyticsService.getCustomerActivity(organizationId, customerId);
        return ResponseEntity.ok(response);
    }

    /**
     * API 4: Get Usage Change Percentage
     * GET /api/metering/analytics/usage-change
     */
    @GetMapping("/usage-change")
    public ResponseEntity<Double> getUsageChange(
            @RequestHeader("X-Organization-Id") Long organizationId,
            @RequestParam Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        
        log.info("GET /usage-change - org: {}, customer: {}", organizationId, customerId);
        
        if (startDate == null) {
            startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if (endDate == null) {
            endDate = Instant.now();
        }

        Double changePercentage = analyticsService.getUsageChangePercentage(organizationId, customerId, startDate, endDate);
        return ResponseEntity.ok(changePercentage);
    }
}
