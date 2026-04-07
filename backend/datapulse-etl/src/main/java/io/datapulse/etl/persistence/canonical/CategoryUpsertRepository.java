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
            INSERT INTO category (workspace_id, marketplace_connection_id, external_category_id,
                                  name, parent_category_id, marketplace_type,
                                  job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (workspace_id, marketplace_type, external_category_id) DO UPDATE SET
                marketplace_connection_id = EXCLUDED.marketplace_connection_id,
                name = EXCLUDED.name,
                parent_category_id = EXCLUDED.parent_category_id,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (category.marketplace_connection_id, category.name,
                   category.parent_category_id)
                IS DISTINCT FROM
                  (EXCLUDED.marketplace_connection_id, EXCLUDED.name,
                   EXCLUDED.parent_category_id)
            """;

    public void batchUpsert(List<CategoryEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setLong(2, e.getMarketplaceConnectionId());
                    ps.setString(3, e.getExternalCategoryId());
                    ps.setString(4, e.getName());
                    ps.setObject(5, e.getParentCategoryId());
                    ps.setString(6, e.getMarketplaceType());
                    ps.setLong(7, e.getJobExecutionId());
                });
    }
}
