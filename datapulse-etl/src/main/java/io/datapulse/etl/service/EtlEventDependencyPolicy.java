package io.datapulse.etl.service;

import io.datapulse.core.service.EtlExecutionAuditService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.exception.EtlEventDependencyNotSatisfiedException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlEventDependencyPolicy {

  private static final long MAX_BACKOFF_MILLIS = 30_000L;

  private final EtlExecutionAuditService etlExecutionAuditService;
  private final EtlSourceRegistry etlSourceRegistry;

  @Value("${etl.dependency.max-attempts:3}")
  private int maxAttempts;

  @Value("${etl.dependency.retry-delay-millis:2000}")
  private long retryDelayMillis;

  public void assertDependenciesSatisfiedOrRetry(EtlSourceExecution execution) {
    Set<MarketplaceEvent> requiredEvents = execution.event().requiredEvents();

    if (requiredEvents.isEmpty()) {
      return;
    }

    long accountId = execution.accountId();
    MarketplaceType marketplace = execution.marketplace();
    MarketplaceEvent targetEvent = execution.event();

    log.info(
        "Checking ETL dependencies: accountId={}, marketplace={}, targetEvent={}, requiredEvents={}",
        accountId,
        marketplace,
        targetEvent,
        requiredEvents
    );

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      boolean allSatisfied = requiredEvents.stream()
          .allMatch(requiredEvent ->
              hasAnyExecutionForEvent(accountId, marketplace, requiredEvent)
          );

      if (allSatisfied) {
        log.info(
            "All ETL dependencies satisfied: accountId={}, marketplace={}, targetEvent={}, attemptsUsed={}",
            accountId,
            marketplace,
            targetEvent,
            attempt
        );
        return;
      }

      if (attempt < maxAttempts) {
        backoffSleep(attempt);
      }
    }

    throw new EtlEventDependencyNotSatisfiedException(
        accountId,
        marketplace,
        targetEvent,
        requiredEvents
    );
  }

  private boolean hasAnyExecutionForEvent(
      long accountId,
      MarketplaceType marketplace,
      MarketplaceEvent requiredEvent
  ) {
    List<String> sourceIds = etlSourceRegistry.getSources(requiredEvent)
        .stream()
        .filter(src -> src.marketplace() == marketplace)
        .map(RegisteredSource::sourceId)
        .distinct()
        .toList();

    if (sourceIds.isEmpty()) {
      String message = String.format(
          "ETL dependency misconfiguration: no registered sources for required event (accountId=%d, marketplace=%s, requiredEvent=%s)",
          accountId,
          marketplace,
          requiredEvent
      );
      log.error(message);
      throw new IllegalStateException(message);
    }

    log.info(
        "Checking ETL dependency: accountId={}, marketplace={}, requiredEvent={}, sourceCount={}",
        accountId,
        marketplace,
        requiredEvent,
        sourceIds.size()
    );

    if (log.isDebugEnabled()) {
      log.debug("Dependency sourceIds resolved for {} -> {}", requiredEvent, sourceIds);
    }

    boolean exists = etlExecutionAuditService.existsExecutionForSources(
        accountId,
        marketplace,
        requiredEvent.name(),
        sourceIds
    );

    if (!exists) {
      log.warn(
          "Dependency NOT satisfied: no audit executions found (accountId={}, marketplace={}, requiredEvent={}, sourceIds={})",
          accountId,
          marketplace,
          requiredEvent,
          sourceIds
      );
    } else {
      log.info(
          "Dependency satisfied: audit executions present (accountId={}, marketplace={}, requiredEvent={})",
          accountId,
          marketplace,
          requiredEvent
      );
    }

    return exists;
  }

  private void backoffSleep(int attempt) {
    long baseDelay = retryDelayMillis <= 0 ? 1000L : retryDelayMillis;
    long delay = baseDelay;

    if (attempt > 1) {
      delay = baseDelay * (1L << (attempt - 1));
    }

    if (delay > MAX_BACKOFF_MILLIS) {
      delay = MAX_BACKOFF_MILLIS;
    }

    log.info("ETL dependency not satisfied yet, will retry: attempt={}, delayMs={}", attempt,
        delay);

    long wakeUpAt = System.currentTimeMillis() + delay;

    try {
      Thread.sleep(delay);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      long remaining = Math.max(0L, wakeUpAt - System.currentTimeMillis());
      log.warn("ETL dependency wait was interrupted (attempt={}, remainingMs={})", attempt,
          remaining);
    }
  }
}
