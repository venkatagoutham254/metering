package aforo.metering.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Analytics repository to query ingestion_event table for usage metrics.
 * Uses secondary datasource (data_ingestion_db) to read ingestion_event.
 */
@Repository
@Slf4j
public class MeteringAnalyticsRepository {

    private final EntityManager entityManager;

    public MeteringAnalyticsRepository(@Qualifier("secondaryEntityManager") EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Get usage count for a customer in a specific time period
     */
    @Transactional(readOnly = true)
    public BigDecimal getCustomerUsage(Long orgId, Long customerId, Instant from, Instant to) {
        String sql = "SELECT COUNT(*)::numeric FROM ingestion_event " +
                    "WHERE status = 'SUCCESS' " +
                    "AND organization_id = :org " +
                    "AND customer_id = :customer " +
                    "AND timestamp >= :from AND timestamp < :to";

        log.debug("Getting customer usage: org={}, customer={}, from={}, to={}", orgId, customerId, from, to);

        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("org", orgId.intValue());
        q.setParameter("customer", customerId.intValue());
        q.setParameter("from", from);
        q.setParameter("to", to);

        Object single = q.getSingleResult();
        BigDecimal count = single != null ? (BigDecimal) single : BigDecimal.ZERO;
        
        log.debug("Found {} events for customer {}", count, customerId);
        
        return count;
    }

    /**
     * Get usage counts for multiple customers in organization
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getCustomersUsage(Long orgId, List<Long> customerIds, Instant from, Instant to) {
        if (customerIds == null || customerIds.isEmpty()) {
            return new HashMap<>();
        }

        String sql = "SELECT customer_id, COUNT(*)::numeric as usage_count " +
                    "FROM ingestion_event " +
                    "WHERE status = 'SUCCESS' " +
                    "AND organization_id = :org " +
                    "AND customer_id IN (:customers) " +
                    "AND timestamp >= :from AND timestamp < :to " +
                    "GROUP BY customer_id";

        log.debug("Getting usage for {} customers in org {}", customerIds.size(), orgId);

        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("org", orgId.intValue());
        
        // Convert List<Long> to List<Integer> for Postgres compatibility
        List<Integer> customerIdInts = customerIds.stream()
                .map(Long::intValue)
                .toList();
        q.setParameter("customers", customerIdInts);
        q.setParameter("from", from);
        q.setParameter("to", to);

        @SuppressWarnings("unchecked")
        List<Object[]> results = q.getResultList();
        
        Map<Long, BigDecimal> usageMap = new HashMap<>();
        for (Object[] row : results) {
            Long customerId = ((Number) row[0]).longValue();
            BigDecimal count = (BigDecimal) row[1];
            usageMap.put(customerId, count);
        }

        // Add zero counts for customers with no usage
        for (Long customerId : customerIds) {
            usageMap.putIfAbsent(customerId, BigDecimal.ZERO);
        }
        
        return usageMap;
    }

    /**
     * Get last activity timestamp for a customer
     */
    @Transactional(readOnly = true)
    public Instant getLastActivityTimestamp(Long orgId, Long customerId) {
        String sql = "SELECT MAX(timestamp) FROM ingestion_event " +
                    "WHERE status = 'SUCCESS' " +
                    "AND organization_id = :org " +
                    "AND customer_id = :customer";

        log.debug("Getting last activity for customer: {}", customerId);

        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("org", orgId.intValue());
        q.setParameter("customer", customerId.intValue());

        Object result = q.getSingleResult();
        return result != null ? (Instant) result : null;
    }

    /**
     * Get last activity timestamps for multiple customers
     */
    @Transactional(readOnly = true)
    public Map<Long, Instant> getCustomersLastActivity(Long orgId, List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return new HashMap<>();
        }

        String sql = "SELECT customer_id, MAX(timestamp) as last_activity " +
                    "FROM ingestion_event " +
                    "WHERE status = 'SUCCESS' " +
                    "AND organization_id = :org " +
                    "AND customer_id IN (:customers) " +
                    "GROUP BY customer_id";

        log.debug("Getting last activity for {} customers", customerIds.size());

        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("org", orgId.intValue());
        
        List<Integer> customerIdInts = customerIds.stream()
                .map(Long::intValue)
                .toList();
        q.setParameter("customers", customerIdInts);

        @SuppressWarnings("unchecked")
        List<Object[]> results = q.getResultList();
        
        Map<Long, Instant> activityMap = new HashMap<>();
        for (Object[] row : results) {
            Long customerId = ((Number) row[0]).longValue();
            Instant lastActivity = row[1] != null ? (Instant) row[1] : null;
            if (lastActivity != null) {
                activityMap.put(customerId, lastActivity);
            }
        }
        
        return activityMap;
    }
}
