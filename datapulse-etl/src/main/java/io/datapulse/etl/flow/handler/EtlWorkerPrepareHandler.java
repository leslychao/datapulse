package io.datapulse.etl.flow.handler;



import static io.datapulse.etl.EtlFlowHeaders.HDR_EVENT;
import static io.datapulse.etl.EtlFlowHeaders.HDR_MARKETPLACE;
import static io.datapulse.etl.EtlFlowHeaders.HDR_OUTCOME;
import static io.datapulse.etl.EtlFlowHeaders.HDR_RAW_TABLE;
import static io.datapulse.etl.EtlFlowHeaders.HDR_REQUEST_ID;
import static io.datapulse.etl.EtlFlowHeaders.HDR_SOURCE_ID;
import static io.datapulse.etl.EtlFlowHeaders.HDR_TTL_MILLIS;
import static io.datapulse.etl.EtlFlowHeaders.HDR_WORK;

import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.event.EtlSourceRegistry;

import io.datapulse.etl.flow.EtlOutcome;
import io.datapulse.etl.flow.repository.EtlExecutionRepo;
import io.datapulse.etl.flow.repository.EtlSourceStateRepo;
import io.datapulse.etl.flow.repository.EtlSourceStateRepo.SourceState;
import io.datapulse.marketplaces.dto.Snapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class EtlWorkerPrepareHandler {

  private final EtlExecutionRepo execRepo;
  private final EtlSourceStateRepo stateRepo;
  private final EtlSourceRegistry registry;

  private final long minBackoffMillis = 1_000L;
  private final long maxBackoffMillis = 60_000L;

  public EtlWorkerPrepareHandler(
      EtlExecutionRepo execRepo,
      EtlSourceStateRepo stateRepo,
      EtlSourceRegistry registry
  ) {
    this.execRepo = execRepo;
    this.stateRepo = stateRepo;
    this.registry = registry;
  }

  public Message<List<Snapshot<?>>> prepare(Message<EtlSourceExecution> msg) {
    EtlSourceExecution work = msg.getPayload();
    Instant now = Instant.now();

    if (execRepo.isTerminal(work.requestId())) {
      return outcome(msg, work, Collections.emptyList(), EtlOutcome.SKIP, null);
    }

    SourceState st = stateRepo.load(work.requestId(), work.event(), work.sourceId());

    if (st.isTerminal()) {
      return outcome(msg, work, Collections.emptyList(), EtlOutcome.SKIP, null);
    }

    if (st.isRetryScheduled() && st.nextAttemptAt() != null && now.isBefore(st.nextAttemptAt())) {
      long ttl = Math.max(1L, Duration.between(now, st.nextAttemptAt()).toMillis());
      ttl = clamp(ttl, minBackoffMillis, maxBackoffMillis);
      return outcome(msg, work, Collections.emptyList(), EtlOutcome.WAIT, ttl);
    }

    // Acquire (redelivery-safe):
    // - IN_PROGRESS allow
    // - else CAS NEW|RETRY_SCHEDULED -> IN_PROGRESS
    if (!stateRepo.tryAcquireForProcessing(work, st.attempt(), st.status())) {
      return outcome(msg, work, Collections.emptyList(), EtlOutcome.SKIP, null);
    }

    var registered = registry.getSources(work.event()).stream()
        .filter(s -> s.sourceId().equals(work.sourceId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown sourceId=" + work.sourceId()));

    List<Snapshot<?>> snapshots = registered.source().fetchSnapshots(
        work.accountId(),
        work.event(),
        work.dateFrom(),
        work.dateTo()
    );

    return MessageBuilder
        .withPayload(snapshots)
        .copyHeaders(msg.getHeaders())
        .setHeader(HDR_WORK, work)
        .setHeader(HDR_REQUEST_ID, work.requestId())
        .setHeader(HDR_EVENT, work.event().name())
        .setHeader(HDR_SOURCE_ID, work.sourceId())
        .setHeader(HDR_RAW_TABLE, registered.rawTable())
        .setHeader(HDR_MARKETPLACE, registered.marketplace())
        .build();
  }

  private static Message<List<Snapshot<?>>> outcome(
      Message<EtlSourceExecution> original,
      EtlSourceExecution work,
      List<Snapshot<?>> payload,
      EtlOutcome outcome,
      Long ttlMillis
  ) {
    MessageBuilder<List<Snapshot<?>>> b = MessageBuilder
        .withPayload(payload)
        .copyHeaders(original.getHeaders())
        .setHeader(HDR_WORK, work)
        .setHeader(HDR_REQUEST_ID, work.requestId())
        .setHeader(HDR_EVENT, work.event().name())
        .setHeader(HDR_SOURCE_ID, work.sourceId())
        .setHeader(HDR_OUTCOME, outcome.name());

    if (ttlMillis != null) {
      b.setHeader(HDR_TTL_MILLIS, ttlMillis);
    } else {
      b.removeHeader(HDR_TTL_MILLIS);
    }
    return b.build();
  }

  private static long clamp(long v, long min, long max) {
    return Math.max(min, Math.min(max, v));
  }
}