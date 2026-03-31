package io.datapulse.etl.persistence.canonical;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductMasterUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO product_master (workspace_id, external_code, name, brand,
                                        job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (workspace_id, external_code) DO UPDATE SET
                name = EXCLUDED.name,
                brand = EXCLUDED.brand,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (product_master.name, product_master.brand)
                IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.brand)
            """;

    public void batchUpsert(List<ProductMasterEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setString(2, e.getExternalCode());
                    ps.setString(3, e.getName());
                    ps.setString(4, e.getBrand());
                    ps.setLong(5, e.getJobExecutionId());
                });
    }
}
