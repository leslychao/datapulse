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
            INSERT INTO warehouse (marketplace_connection_id, external_warehouse_id, name,
                                   warehouse_type, marketplace_type,
                                   job_execution_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (marketplace_connection_id, external_warehouse_id) DO UPDATE SET
                name = EXCLUDED.name,
                warehouse_type = EXCLUDED.warehouse_type,
                marketplace_type = EXCLUDED.marketplace_type,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = now()
            WHERE (warehouse.name, warehouse.warehouse_type, warehouse.marketplace_type)
                IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.warehouse_type, EXCLUDED.marketplace_type)
            """;

    public void batchUpsert(List<WarehouseEntity> entities) {
        jdbc.batchUpdate(UPSERT, entities, DEFAULT_BATCH_SIZE,
                (ps, e) -> {
                    ps.setLong(1, e.getMarketplaceConnectionId());
                    ps.setString(2, e.getExternalWarehouseId());
                    ps.setString(3, e.getName());
                    ps.setString(4, e.getWarehouseType());
                    ps.setString(5, e.getMarketplaceType());
                    ps.setLong(6, e.getJobExecutionId());
                });
    }
}
