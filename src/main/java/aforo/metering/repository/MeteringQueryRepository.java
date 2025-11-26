package aforo.metering.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Repository for metering-specific queries on ingestion_event table.
 * Uses secondary datasource (data_ingestion_db) to read ingestion_event.
 */
@Repository
public class MeteringQueryRepository {

    private final EntityManager entityManager;

    public MeteringQueryRepository(@Qualifier("secondaryEntityManager") EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Get distinct rate plan IDs that have events in the specified time range.
     * This is useful for batch processing metering calculations.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Long> findDistinctRatePlanIds(Long orgId, Instant from, Instant to) {
        String sql = "SELECT DISTINCT rate_plan_id FROM ingestion_event " +
                     "WHERE status = 'SUCCESS' " +
                     "AND organization_id = :org " +
                     "AND timestamp >= :from " +
                     "AND timestamp < :to " +
                     "AND rate_plan_id IS NOT NULL";
        
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("org", orgId.intValue());
        q.setParameter("from", from);
        q.setParameter("to", to);
        
        List<Number> results = q.getResultList();
        return results.stream()
                .map(Number::longValue)
                .toList();
    }
    
    /**
     * Get distinct subscription IDs for a specific rate plan.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Long> findSubscriptionsByRatePlan(Long orgId, Long ratePlanId, Instant from, Instant to) {
        String sql = "SELECT DISTINCT subscription_id FROM ingestion_event " +
                     "WHERE status = 'SUCCESS' " +
                     "AND organization_id = :org " +
                     "AND rate_plan_id = :rp " +
                     "AND timestamp >= :from " +
                     "AND timestamp < :to " +
                     "AND subscription_id IS NOT NULL";
        
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("org", orgId.intValue());
        q.setParameter("rp", ratePlanId.intValue());
        q.setParameter("from", from);
        q.setParameter("to", to);
        
        List<Number> results = q.getResultList();
        return results.stream()
                .map(Number::longValue)
                .toList();
    }
    
    /**
     * Check if there are any new events since the last metering run.
     */
    @Transactional(readOnly = true)
    public boolean hasNewEvents(Long orgId, Instant since) {
        String sql = "SELECT EXISTS(SELECT 1 FROM ingestion_event " +
                     "WHERE status = 'SUCCESS' " +
                     "AND organization_id = :org " +
                     "AND timestamp >= :since " +
                     "LIMIT 1)";
        
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("org", orgId.intValue());
        q.setParameter("since", since);
        
        Boolean result = (Boolean) q.getSingleResult();
        return result != null && result;
    }
}
