package io.datapulse.analytics.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.datapulse.platform.etl.PostIngestMaterializationResult;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MaterializationService {

    private final List<AnalyticsMaterializer> orderedMaterializers;

    public MaterializationService(List<AnalyticsMaterializer> materializers) {
        this.orderedMaterializers = materializers.stream()
                .sorted(Comparator.comparing(m -> m.phase().ordinal()))
                .toList();
    }

    public void runFullRematerialization() {
        log.info("Full re-materialization started: tables={}", orderedMaterializers.size());

        int successCount = 0;
        for (AnalyticsMaterializer materializer : orderedMaterializers) {
            String table = materializer.tableName();
            try {
                log.info("Materializing table: table={}, phase={}", table, materializer.phase());
                materializer.materializeFull();
                successCount++;
                log.info("Materialized table: table={}", table);
            } catch (Exception e) {
                log.error("Materialization failed: table={}", table, e);
                throw new IllegalStateException(
                        "Full re-materialization aborted at table=%s".formatted(table), e);
            }
        }

        log.info("Full re-materialization completed: succeeded={}", successCount);
    }

    public PostIngestMaterializationResult runIncrementalMaterialization(long jobExecutionId) {
        log.info("Incremental materialization started: jobExecutionId={}", jobExecutionId);

        List<String> failedTables = new ArrayList<>();
        for (AnalyticsMaterializer materializer : orderedMaterializers) {
            String table = materializer.tableName();
            try {
                materializer.materializeIncremental(jobExecutionId);
            } catch (Exception e) {
                log.error("Incremental materialization failed: table={}, jobExecutionId={}",
                        table, jobExecutionId, e);
                failedTables.add(table);
            }
        }

        log.info("Incremental materialization completed: jobExecutionId={}, failedTables={}",
                jobExecutionId, failedTables.size());
        if (failedTables.isEmpty()) {
            return PostIngestMaterializationResult.ok();
        }
        return PostIngestMaterializationResult.partialFailure(failedTables);
    }
}
