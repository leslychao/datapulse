package io.datapulse.etl.materializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Materializes canonical data from PostgreSQL to ClickHouse.
 *
 * <p><b>Phase B stub:</b> basic structure is in place. Full materialization
 * (per-domain fact/dim INSERT, re-aggregation of marts) will be implemented
 * when ClickHouse schema and mart definitions are finalized.</p>
 *
 * <p><b>Not yet wired</b> into the ETL pipeline. Will be called from
 * {@link io.datapulse.etl.domain.SubSourceRunner} after canonical UPSERT
 * completes for each page/batch once Phase B work begins.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickHouseMaterializer {

    private final JdbcTemplate clickhouseJdbcTemplate;

    /**
     * Materializes records for the given job execution and event type to ClickHouse.
     * Phase A: logs intent, actual materialization is deferred to Phase B.
     *
     * @param jobExecutionId the ETL run identifier
     * @param eventType      ETL event being materialized
     * @param recordCount    number of canonical records to materialize
     */
    public void materialize(long jobExecutionId, String eventType, int recordCount) {
        // Phase A: materialization is a no-op. ClickHouse schema and fact table
        // INSERT logic will be implemented as part of Phase B analytics work.
        log.debug("Materialization stub: jobExecutionId={}, eventType={}, recordCount={}",
                jobExecutionId, eventType, recordCount);
    }
}
