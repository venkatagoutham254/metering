package aforo.metering.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository to fetch organization IDs from the ingestion database.
 * Used by scheduled jobs to determine which organizations to process.
 */
@Repository
@RequiredArgsConstructor
public class OrganizationRepository {

    @Qualifier("secondaryJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /**
     * Get all distinct organization IDs that have ingestion events.
     * This is used to determine which organizations to process in scheduled jobs.
     * 
     * @return List of organization IDs
     */
    public List<Long> findAllOrganizationIds() {
        String sql = "SELECT DISTINCT organization_id FROM ingestion_event " +
                     "WHERE organization_id IS NOT NULL " +
                     "ORDER BY organization_id";
        
        return jdbcTemplate.queryForList(sql, Long.class);
    }
}
