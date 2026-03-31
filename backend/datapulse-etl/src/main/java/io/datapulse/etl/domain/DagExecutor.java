package io.datapulse.etl.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * DAG executor with level-based parallelism.
 *
 * <p>Events on the same DAG level run in parallel via {@link CompletableFuture}.
 * A barrier ({@code CompletableFuture.allOf}) between levels ensures all events
 * of the current level complete before the next level starts.</p>
 *
 * <p>Error propagation rules:
 * <ul>
 *   <li>Hard dependency failed → dependent event is <b>skipped</b></li>
 *   <li>Soft dependency failed → dependent event <b>runs</b> with warning</li>
 *   <li>One event failed on a level, others OK → barrier waits for all;
 *       next-level events run if their hard dependencies are satisfied</li>
 * </ul>
 */
@Slf4j
@Service
public class DagExecutor {

    private final EventRunner eventRunner;
    private final Executor etlExecutor;

    public DagExecutor(EventRunner eventRunner,
                       @Qualifier("etlExecutor") Executor etlExecutor) {
        this.eventRunner = eventRunner;
        this.etlExecutor = etlExecutor;
    }

    /**
     * Executes the full DAG for the given context.
     *
     * @return per-event results (including skipped events)
     */
    public Map<EtlEventType, EventResult> execute(IngestContext context) {
        Map<EtlEventType, EventResult> allResults = new EnumMap<>(EtlEventType.class);

        List<DagLevel> levels = DagDefinition.levelsFor(context.scope());

        for (DagLevel level : levels) {
            log.info("DAG level {} started: events={}, jobExecutionId={}",
                    level.level(), level.events(), context.jobExecutionId());

            List<CompletableFuture<EventResult>> futures = new ArrayList<>();

            for (EtlEventType eventType : level.events()) {
                if (context.isEventCompleted(eventType)) {
                    log.info("Skipping completed event (checkpoint resume): eventType={}", eventType);
                    allResults.put(eventType, EventResult.completed(eventType, List.of()));
                    continue;
                }

                String skipReason = checkHardDependencies(eventType, allResults);
                if (skipReason != null) {
                    log.warn("Skipping event due to failed hard dependency: eventType={}, reason={}",
                            eventType, skipReason);
                    EventResult skipped = EventResult.skipped(eventType, skipReason);
                    allResults.put(eventType, skipped);
                    continue;
                }

                checkSoftDependencies(eventType, allResults);

                CompletableFuture<EventResult> future = CompletableFuture.supplyAsync(
                        () -> runWithMdc(eventType, context), etlExecutor);
                futures.add(future);
            }

            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

                for (CompletableFuture<EventResult> future : futures) {
                    EventResult result = future.join();
                    allResults.put(result.eventType(), result);
                }
            }

            log.info("DAG level {} completed: jobExecutionId={}", level.level(), context.jobExecutionId());
        }

        return allResults;
    }

    /**
     * Runs event with MDC context so parallel logs are filterable by event_type.
     */
    private EventResult runWithMdc(EtlEventType eventType, IngestContext context) {
        MDC.put("event_type", eventType.name());
        MDC.put("connection_id", String.valueOf(context.connectionId()));
        MDC.put("job_execution_id", String.valueOf(context.jobExecutionId()));
        try {
            return eventRunner.run(eventType, context);
        } finally {
            MDC.remove("event_type");
            MDC.remove("connection_id");
            MDC.remove("job_execution_id");
        }
    }

    /**
     * Checks all hard dependencies for the event. Returns skip reason if any
     * hard dependency is not satisfied, null if all OK.
     */
    private String checkHardDependencies(EtlEventType eventType,
                                          Map<EtlEventType, EventResult> completedResults) {
        for (EtlEventType dep : DagDefinition.hardDependenciesOf(eventType)) {
            EventResult depResult = completedResults.get(dep);
            if (depResult == null) {
                return "hard dep %s not executed".formatted(dep);
            }
            if (depResult.isFailed() || depResult.isSkipped()) {
                return "hard dep %s %s".formatted(dep, depResult.status());
            }
        }
        return null;
    }

    /**
     * Logs warnings for failed soft dependencies. Does not skip the event.
     */
    private void checkSoftDependencies(EtlEventType eventType,
                                        Map<EtlEventType, EventResult> completedResults) {
        for (EtlEventType dep : DagDefinition.softDependenciesOf(eventType)) {
            EventResult depResult = completedResults.get(dep);
            if (depResult != null && (depResult.isFailed() || depResult.isSkipped())) {
                log.warn("Soft dependency failed, continuing: eventType={}, softDep={}, depStatus={}",
                        eventType, dep, depResult.status());
            }
        }
    }
}
