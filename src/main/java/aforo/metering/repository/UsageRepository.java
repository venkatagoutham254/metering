package aforo.metering.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*)::numeric FROM ingestion_event WHERE status = 'SUCCESS' AND organization_id = :org");
        sql.append(" AND timestamp >= :from AND timestamp < :to");
        if (subscriptionId != null) sql.append(" AND subscription_id = :sub");
        if (productId != null) sql.append(" AND product_id = :prod");
        if (ratePlanId != null) sql.append(" AND rate_plan_id = :rp");
        if (metricId != null) sql.append(" AND billable_metric_id = :metric");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("org", orgId.intValue());
        q.setParameter("from", from);
        q.setParameter("to", to);
        if (subscriptionId != null) q.setParameter("sub", subscriptionId.intValue());
        if (productId != null) q.setParameter("prod", productId.intValue());
        if (ratePlanId != null) q.setParameter("rp", ratePlanId.intValue());
        if (metricId != null) q.setParameter("metric", metricId.intValue());

        Object single = q.getSingleResult();
        return single != null ? (BigDecimal) single : BigDecimal.ZERO;
    }
}
