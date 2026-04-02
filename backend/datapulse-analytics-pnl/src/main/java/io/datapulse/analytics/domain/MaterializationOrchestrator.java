package io.datapulse.analytics.domain;

import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterializationOrchestrator {

  private final List<AnalyticsMaterializer> materializers;

  public void runFull() {
    log.info("Starting full re-materialization");
    long start = System.currentTimeMillis();

    List<AnalyticsMaterializer> ordered = materializers.stream()
        .sorted(Comparator.comparingInt((AnalyticsMaterializer m) -> m.phase().ordinal())
            .thenComparingInt(AnalyticsMaterializer::order))
        .toList();

    for (AnalyticsMaterializer materializer : ordered) {
      try {
        log.info("Full materialization: table={}, phase={}",
            materializer.tableName(), materializer.phase());
        materializer.materializeFull();
      } catch (Exception e) {
        log.error("Full materialization failed: table={}", materializer.tableName(), e);
        throw e;
      }
    }

    long elapsed = System.currentTimeMillis() - start;
    log.info("Full re-materialization completed: tables={}, elapsed={}ms",
        ordered.size(), elapsed);
  }

  public void runIncremental(long jobExecutionId) {
    log.info("Starting incremental materialization: jobExecutionId={}",
        jobExecutionId);

    List<AnalyticsMaterializer> ordered = materializers.stream()
        .sorted(Comparator.comparingInt((AnalyticsMaterializer m) -> m.phase().ordinal())
            .thenComparingInt(AnalyticsMaterializer::order))
        .toList();

    for (AnalyticsMaterializer materializer : ordered) {
      try {
        materializer.materializeIncremental(jobExecutionId);
      } catch (Exception e) {
        log.error("Incremental materialization failed: table={}, jobExecutionId={}",
            materializer.tableName(), jobExecutionId, e);
        throw e;
      }
    }

    log.info("Incremental materialization completed: jobExecutionId={}",
        jobExecutionId);
  }
}
