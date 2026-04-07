package io.datapulse.analytics.domain.materializer.dim;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.config.AnalyticsProperties;
import io.datapulse.analytics.domain.AnalyticsMaterializer;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import io.datapulse.analytics.domain.MaterializationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DimCategoryMaterializer implements AnalyticsMaterializer {

    private static final String TABLE = "dim_category";

    private static final String PG_QUERY = """
            SELECT c.workspace_id,
                   c.id                       AS category_id,
                   c.marketplace_connection_id AS connection_id,
                   c.external_category_id,
                   c.name,
                   c.parent_category_id,
                   c.marketplace_type
            FROM category c
            ORDER BY c.id
            LIMIT :limit OFFSET :offset
            """;

    private static final String CH_INSERT = """
            INSERT INTO %s
            (workspace_id, category_id, connection_id, external_category_id, name,
             parent_category_id, marketplace_type, ver)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final MaterializationJdbc jdbc;
    private final AnalyticsProperties properties;

    @Override
    public void materializeFull() {
        long ver = Instant.now().toEpochMilli();
        final int[] total = {0};

        jdbc.fullMaterializeWithSwap(TABLE, staging -> {
            String chInsert = CH_INSERT.formatted(staging);
            int offset = 0;

            while (true) {
                List<Map<String, Object>> rows = jdbc.pg().queryForList(PG_QUERY,
                    Map.of("limit", properties.batchSize(), "offset", offset));
                if (rows.isEmpty()) {
                    break;
                }

                jdbc.ch().batchUpdate(chInsert, rows, rows.size(), (ps, row) -> {
                    ps.setInt(1, ((Number) row.get("workspace_id")).intValue());
                    ps.setLong(2, ((Number) row.get("category_id")).longValue());
                    ps.setInt(3, ((Number) row.get("connection_id")).intValue());
                    ps.setString(4, (String) row.get("external_category_id"));
                    ps.setString(5, (String) row.get("name"));
                    Number parentId = (Number) row.get("parent_category_id");
                    if (parentId != null) {
                        ps.setLong(6, parentId.longValue());
                    } else {
                        ps.setNull(6, java.sql.Types.BIGINT);
                    }
                    ps.setString(7, (String) row.get("marketplace_type"));
                    ps.setLong(8, ver);
                });

                total[0] += rows.size();
                offset += properties.batchSize();
            }
        });

        log.info("Materialized dim_category: rows={}", total[0]);
    }

    @Override
    public void materializeIncremental(long jobExecutionId) {
        long ver = Instant.now().toEpochMilli();
        String chInsert = CH_INSERT.formatted(TABLE);

        List<Map<String, Object>> rows = jdbc.pg().queryForList("""
                SELECT c.workspace_id,
                       c.id                       AS category_id,
                       c.marketplace_connection_id AS connection_id,
                       c.external_category_id,
                       c.name,
                       c.parent_category_id,
                       c.marketplace_type
                FROM category c
                WHERE c.job_execution_id = :jobExecutionId
                """, Map.of("jobExecutionId", jobExecutionId));

        if (rows.isEmpty()) {
            return;
        }

        jdbc.ch().batchUpdate(chInsert, rows, rows.size(), (ps, row) -> {
            ps.setInt(1, ((Number) row.get("workspace_id")).intValue());
            ps.setLong(2, ((Number) row.get("category_id")).longValue());
            ps.setInt(3, ((Number) row.get("connection_id")).intValue());
            ps.setString(4, (String) row.get("external_category_id"));
            ps.setString(5, (String) row.get("name"));
            Number parentId = (Number) row.get("parent_category_id");
            if (parentId != null) {
                ps.setLong(6, parentId.longValue());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            ps.setString(7, (String) row.get("marketplace_type"));
            ps.setLong(8, ver);
        });

        log.info("Incremental dim_category: jobExecutionId={}, rows={}", jobExecutionId, rows.size());
    }

    @Override
    public String tableName() {
        return TABLE;
    }

    @Override
    public MaterializationPhase phase() {
        return MaterializationPhase.DIMENSION;
    }
}
