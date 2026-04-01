package io.datapulse.analytics.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;

import io.datapulse.analytics.api.ProvenanceEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProvenanceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FIND_ENTRY_SQL = """
            SELECT
                cfe.id,
                cfe.connection_id,
                cfe.source_platform,
                cfe.external_entry_id,
                cfe.entry_type,
                cfe.posting_id,
                cfe.order_id,
                cfe.seller_sku_id,
                cfe.revenue_amount,
                cfe.marketplace_commission_amount,
                cfe.acquiring_commission_amount,
                cfe.logistics_cost_amount,
                cfe.storage_cost_amount,
                cfe.penalties_amount,
                cfe.acceptance_cost_amount,
                cfe.marketing_cost_amount,
                cfe.other_marketplace_charges_amount,
                cfe.compensation_amount,
                cfe.refund_amount,
                cfe.net_payout,
                cfe.entry_date,
                cfe.job_execution_id
            FROM canonical_finance_entry cfe
            JOIN marketplace_connection mc ON cfe.connection_id = mc.id
            WHERE cfe.id = :entryId
              AND mc.workspace_id = :workspaceId
            """;

    private static final String FIND_S3_KEY_SQL = """
            SELECT ji.s3_key, ji.byte_size
            FROM job_item ji
            JOIN job_execution je ON ji.job_execution_id = je.id
            JOIN marketplace_connection mc ON je.connection_id = mc.id
            WHERE ji.job_execution_id = :jobExecutionId
              AND mc.workspace_id = :workspaceId
            ORDER BY ji.page_number
            LIMIT 1
            """;

    public Optional<ProvenanceEntryResponse> findCanonicalEntry(long entryId, long workspaceId) {
        var params = new MapSqlParameterSource()
                .addValue("entryId", entryId)
                .addValue("workspaceId", workspaceId);

        var results = jdbc.query(FIND_ENTRY_SQL, params, (rs, rowNum) -> new ProvenanceEntryResponse(
                rs.getLong("id"),
                rs.getLong("connection_id"),
                rs.getString("source_platform"),
                rs.getString("external_entry_id"),
                rs.getString("entry_type"),
                rs.getString("posting_id"),
                rs.getString("order_id"),
                rs.getObject("seller_sku_id", Long.class),
                rs.getBigDecimal("revenue_amount"),
                rs.getBigDecimal("marketplace_commission_amount"),
                rs.getBigDecimal("acquiring_commission_amount"),
                rs.getBigDecimal("logistics_cost_amount"),
                rs.getBigDecimal("storage_cost_amount"),
                rs.getBigDecimal("penalties_amount"),
                rs.getBigDecimal("acceptance_cost_amount"),
                rs.getBigDecimal("marketing_cost_amount"),
                rs.getBigDecimal("other_marketplace_charges_amount"),
                rs.getBigDecimal("compensation_amount"),
                rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("net_payout"),
                rs.getObject("entry_date", OffsetDateTime.class),
                rs.getObject("job_execution_id", Long.class)
        ));

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<RawFileInfo> findRawFileInfo(long jobExecutionId, long workspaceId) {
        var params = new MapSqlParameterSource()
                .addValue("jobExecutionId", jobExecutionId)
                .addValue("workspaceId", workspaceId);

        var results = jdbc.query(FIND_S3_KEY_SQL, params,
                (rs, rowNum) -> new RawFileInfo(rs.getString("s3_key"), rs.getLong("byte_size")));

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public record RawFileInfo(String s3Key, long byteSize) {}
}
