package aforo.metering.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Repository to count billable events from ingestion_event table.
 * Uses secondary datasource (data_ingestion_db) to read ingestion_event.
 * Note: ingestion_event no longer has 'quantity' field - each row represents one billable event.
 * We COUNT events instead of SUMming quantity.
 */
@Repository
@Slf4j
public class UsageRepository {

    private final EntityManager entityManager;

    public UsageRepository(@Qualifier("secondaryEntityManager") EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public BigDecimal countEvents(Long orgId,
                                  Instant from,
                                  Instant to,
                                  Long subscriptionId,
                                  Long productId,
                                  Long ratePlanId,
                                  Long metricId) {
        // Only filter by organization_id, subscription_id, timestamp, and status
        // Product/RatePlan/Metric IDs are derived from subscription - don't filter by them
        // as ingestion events may not have these fields populated
        StringBuilder sql = new StringBuilder("SELECT COUNT(*)::numeric FROM ingestion_event WHERE status = 'SUCCESS' AND organization_id = :org");
        sql.append(" AND timestamp >= :from AND timestamp < :to");
        if (subscriptionId != null) sql.append(" AND subscription_id = :sub");

        log.debug("Counting events: org={}, subscription={}, from={}, to={}", orgId, subscriptionId, from, to);

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("org", orgId.intValue());
        q.setParameter("from", from);
        q.setParameter("to", to);
        if (subscriptionId != null) q.setParameter("sub", subscriptionId.intValue());

        Object single = q.getSingleResult();
        BigDecimal count = single != null ? (BigDecimal) single : BigDecimal.ZERO;
        
        log.debug("Found {} events for subscription {}", count, subscriptionId);
        
        return count;
    }
}
