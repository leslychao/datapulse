package io.datapulse.etl.persistence.canonical;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WarehouseUpsertRepository {

    private final JdbcTemplate jdbc;

    private static final int DEFAULT_BATCH_SIZE = 500;

    private static final String UPSERT = """
            INSERT INTO warehouse (workspace_id, marketplace_connection_id, external_warehouse_id,
                                   name, warehouse_type, marketplace_type,
                                   job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (workspace_id, marketplace_type, external_warehouse_id) DO UPDATE SET
                marketplace_connection_id = EXCLUDED.marketplace_connection_id,
                name = EXCLUDED.name,
                warehouse_type = EXCLUDED.warehouse_type,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (warehouse.marketplace_connection_id, warehouse.name,
                   warehouse.warehouse_type)
                IS DISTINCT FROM
                  (EXCLUDED.marketplace_connection_id, EXCLUDED.name,
                   EXCLUDED.warehouse_type)
            """;

    public void batchUpsert(List<WarehouseEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getWorkspaceId());
                    ps.setLong(2, e.getMarketplaceConnectionId());
                    ps.setString(3, e.getExternalWarehouseId());
                    ps.setString(4, e.getName());
                    ps.setString(5, e.getWarehouseType());
                    ps.setString(6, e.getMarketplaceType());
                    ps.setLong(7, e.getJobExecutionId());
                });
    }
}
