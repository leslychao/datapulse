package io.datapulse.etl.persistence.canonical;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CategoryUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO category (marketplace_connection_id, external_category_id, name,
                                  parent_category_id, marketplace_type,
                                  job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (marketplace_connection_id, external_category_id) DO UPDATE SET
                name = EXCLUDED.name,
                parent_category_id = EXCLUDED.parent_category_id,
                marketplace_type = EXCLUDED.marketplace_type,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (category.name, category.parent_category_id, category.marketplace_type)
                IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.parent_category_id, EXCLUDED.marketplace_type)
            """;

    public void batchUpsert(List<CategoryEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getMarketplaceConnectionId());
                    ps.setString(2, e.getExternalCategoryId());
                    ps.setString(3, e.getName());
                    ps.setObject(4, e.getParentCategoryId());
                    ps.setString(5, e.getMarketplaceType());
                    ps.setLong(6, e.getJobExecutionId());
                });
    }
}
