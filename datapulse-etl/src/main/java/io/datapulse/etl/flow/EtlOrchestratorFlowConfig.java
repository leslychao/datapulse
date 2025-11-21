package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ERRORS;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_EVENT_INGEST_COMPLETED;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_PLAN_FAILED;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EXPECTED_SNAPSHOTS;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MP;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.file.SnapshotCommitBarrier;
import io.datapulse.etl.file.SnapshotCommitBarrier.SnapshotCompletionEvent;
import io.datapulse.etl.flow.dto.EarlySourceFailureContext;
import io.datapulse.etl.flow.dto.EtlSourceExecution;
import io.datapulse.etl.route.EtlSourceRegistry;
import io.datapulse.etl.route.EtlSourceRegistry.RegisteredSource;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
public class EtlOrchestratorFlowConfig {

  private final EtlSourceRegistry etlSourceRegistry;
  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final MessageChannel ingestChannel;
  private final MessageChannel eventIngestCompletedChannel;

  public EtlOrchestratorFlowConfig(
      EtlSourceRegistry etlSourceRegistry,
      SnapshotCommitBarrier snapshotCommitBarrier,
      @Qualifier(CH_ETL_INGEST) MessageChannel ingestChannel,
      @Qualifier(CH_ETL_EVENT_INGEST_COMPLETED) MessageChannel eventIngestCompletedChannel
  ) {
    this.etlSourceRegistry = etlSourceRegistry;
    this.snapshotCommitBarrier = snapshotCommitBarrier;
    this.ingestChannel = ingestChannel;
    this.eventIngestCompletedChannel = eventIngestCompletedChannel;
  }

  public record EtlRunRequest(
      Long accountId,
      String event,
      LocalDate from,
      LocalDate to
  ) {

  }

  private record RequiredField(String name, Object value) {

  }

  private record MaterializationJobKey(
      String requestId,
      Long accountId,
      MarketplaceEvent event
  ) {

  }

  private static final class MaterializationJob {

    final String requestId;
    final Long accountId;
    final MarketplaceEvent event;
    final LocalDate from;
    final LocalDate to;
    final int expectedSnapshots;
    final AtomicInteger completedSnapshots = new AtomicInteger(0);

    MaterializationJob(
        String requestId,
        Long accountId,
        MarketplaceEvent event,
        LocalDate from,
        LocalDate to,
        int expectedSnapshots
    ) {
      this.requestId = requestId;
      this.accountId = accountId;
      this.event = event;
      this.from = from;
      this.to = to;
      this.expectedSnapshots = expectedSnapshots;
    }
  }

  private final Map<MaterializationJobKey, MaterializationJob> materializationJobs =
      new ConcurrentHashMap<>();

  private record MarketplaceKey(
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      MarketplaceType marketplace
  ) {

  }

  private static final class MarketplaceExecutionPlan {

    final ReentrantLock lock = new ReentrantLock();

    final String requestId;
    final Long accountId;
    final MarketplaceEvent event;
    final MarketplaceType marketplace;
    final Map<Integer, List<EtlSourceExecution>> stages;
    final Set<String> completedSourceIds = ConcurrentHashMap.newKeySet();
    int currentOrder;
    boolean failed;

    MarketplaceExecutionPlan(
        String requestId,
        Long accountId,
        MarketplaceEvent event,
        MarketplaceType marketplace,
        List<EtlSourceExecution> executions
    ) {
      this.requestId = requestId;
      this.accountId = accountId;
      this.event = event;
      this.marketplace = marketplace;
      this.stages = executions.stream()
          .collect(
              LinkedHashMap::new,
              (map, exec) -> map
                  .computeIfAbsent(exec.order(), o -> new ArrayList<>())
                  .add(exec),
              Map::putAll
          );
      this.currentOrder = stages.keySet().stream().min(Integer::compareTo).orElse(0);
    }

    boolean isCurrentStageCompleted() {
      List<EtlSourceExecution> list = stages.getOrDefault(currentOrder, List.of());
      for (EtlSourceExecution exec : list) {
        if (!completedSourceIds.contains(exec.sourceId())) {
          return false;
        }
      }
      return true;
    }

    Integer nextOrder() {
      return stages.keySet().stream()
          .filter(o -> o > currentOrder)
          .min(Integer::compareTo)
          .orElse(null);
    }
  }

  private final Map<MarketplaceKey, MarketplaceExecutionPlan> plans =
      new ConcurrentHashMap<>();

  @PostConstruct
  public void registerSnapshotCompletionListener() {
    snapshotCommitBarrier.registerListener(this::onSnapshotCompleted);
  }

  public void markSourceFailedBeforeSnapshot(
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      MarketplaceType marketplace,
      String sourceId
  ) {
    MaterializationJobKey jobKey = new MaterializationJobKey(requestId, accountId, event);
    MarketplaceKey planKey = new MarketplaceKey(requestId, accountId, event, marketplace);

    MaterializationJob removedJob = materializationJobs.remove(jobKey);
    MarketplaceExecutionPlan plan = plans.get(planKey);
    boolean planRemoved = false;

    if (plan != null) {
      plan.lock.lock();
      try {
        plan.failed = true;
        planRemoved = plans.remove(planKey, plan);
      } finally {
        plan.lock.unlock();
      }
    }

    log.warn(
        "ETL orchestration failed before snapshot registration: requestId={}, accountId={}, "
            + "event={}, marketplace={}, sourceId={}, jobRemoved={}, planRemoved={}",
        requestId,
        accountId,
        event,
        marketplace,
        sourceId,
        removedJob != null,
        planRemoved
    );
  }

  private void onSnapshotCompleted(SnapshotCompletionEvent event) {
    MaterializationJobKey key =
        new MaterializationJobKey(event.requestId(), event.accountId(), event.event());

    MaterializationJob job = materializationJobs.get(key);
    if (job == null) {
      log.debug(
          "Snapshot completed, but materialization job not found: snapshotId={}, key={}",
          event.snapshotId(),
          key
      );
    } else {
      int done = job.completedSnapshots.incrementAndGet();
      if (done > job.expectedSnapshots) {
        log.warn(
            "Snapshot completed over plan: key={}, done={}, expected={}",
            key,
            done,
            job.expectedSnapshots
        );
      }

      Map<String, Object> headers = new LinkedHashMap<>();
      headers.put(HDR_ETL_REQUEST_ID, job.requestId);
      headers.put(HDR_ETL_ACCOUNT_ID, job.accountId);
      headers.put(HDR_ETL_EVENT, job.event.name());
      headers.put(HDR_ETL_DATE_FROM, job.from);
      headers.put(HDR_ETL_DATE_TO, job.to);
      headers.put(HDR_ETL_EXPECTED_SNAPSHOTS, job.expectedSnapshots);

      GenericMessage<String> message = new GenericMessage<>(event.snapshotId(), headers);

      boolean sent = eventIngestCompletedChannel.send(message);
      if (!sent) {
        log.warn(
            "Failed to send ingest-completed tick: snapshotId={}, key={}",
            event.snapshotId(),
            key
        );
      }

      if (done == job.expectedSnapshots) {
        boolean removed = materializationJobs.remove(key, job);
        if (!removed) {
          log.debug("MaterializationJob already removed by another thread: key={}", key);
        } else {
          log.debug(
              "MaterializationJob completed and removed: key={}, expectedSnapshots={}",
              key,
              job.expectedSnapshots
          );
        }
      }
    }

    MarketplaceKey marketplaceKey =
        new MarketplaceKey(
            event.requestId(),
            event.accountId(),
            event.event(),
            event.marketplace()
        );

    MarketplaceExecutionPlan plan = plans.get(marketplaceKey);
    if (plan == null) {
      return;
    }

    Integer nextOrderToStart = null;

    plan.lock.lock();
    try {
      if (plan.failed) {
        return;
      }

      plan.completedSourceIds.add(event.sourceId());

      if (!plan.isCurrentStageCompleted()) {
        return;
      }

      Integer nextOrder = plan.nextOrder();
      if (nextOrder == null) {
        plans.remove(marketplaceKey);
      } else {
        plan.currentOrder = nextOrder;
        nextOrderToStart = nextOrder;
      }
    } finally {
      plan.lock.unlock();
    }

    if (nextOrderToStart != null) {
      startStage(plan, nextOrderToStart);
    }
  }

  @Bean("etlOrchestrateExecutor")
  public TaskExecutor etlOrchestrateExecutor(
      @Value("${etl.orchestrate.core-pool-size:4}") int corePoolSize,
      @Value("${etl.orchestrate.max-pool-size:8}") int maxPoolSize,
      @Value("${etl.orchestrate.queue-capacity:500}") int queueCapacity
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("etl-orchestrate-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = CH_ETL_ORCHESTRATE)
  public MessageChannel etlOrchestrateChannel(TaskExecutor etlOrchestrateExecutor) {
    return new ExecutorChannel(etlOrchestrateExecutor);
  }

  @Bean(name = CH_ETL_PLAN_FAILED)
  public MessageChannel etlPlanFailedChannel() {
    return new PublishSubscribeChannel();
  }

  @Bean
  public IntegrationFlow etlOrchestratorInboundFlow() {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestPayloadType(EtlRunRequest.class)
            .mappedRequestHeaders("*")
            .statusCodeFunction(m -> HttpStatus.ACCEPTED)
        )
        .enrichHeaders(headers -> headers
            .headerFunction(HDR_ETL_REQUEST_ID, m -> UUID.randomUUID().toString())
        )
        .wireTap(CH_ETL_ORCHESTRATE)
        .handle(EtlRunRequest.class, this::buildAcceptedResponseBody)
        .get();
  }

  @Bean
  public IntegrationFlow etlOrchestrateFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATE)
        .enrichHeaders(headers ->
            headers.header(
                MessageHeaders.ERROR_CHANNEL,
                CH_ETL_ERRORS,
                true
            ))
        .headerFilter(MessageHeaders.REPLY_CHANNEL)
        .handle(EtlRunRequest.class, (request, headers) -> {
          validateRunRequest(request);

          MarketplaceEvent event = MarketplaceEvent.fromString(request.event());
          if (event == null) {
            throw new AppException(
                MessageCodes.ETL_REQUEST_INVALID,
                "event=" + request.event()
            );
          }

          String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);

          log.info(
              "ETL orchestration started: requestId={}, accountId={}, event={}, from={}, to={}",
              requestId,
              request.accountId(),
              event,
              request.from(),
              request.to()
          );

          List<EtlSourceExecution> sourceExecutions = buildSourceExecutions(request, event);
          int expectedSnapshots = sourceExecutions.size();

          registerMaterializationJob(
              requestId,
              request.accountId(),
              event,
              request.from(),
              request.to(),
              expectedSnapshots
          );

          Map<MarketplaceType, List<EtlSourceExecution>> byMarketplace =
              sourceExecutions.stream().collect(
                  LinkedHashMap::new,
                  (map, exec) -> map
                      .computeIfAbsent(exec.marketplace(), m -> new ArrayList<>())
                      .add(exec),
                  Map::putAll
              );

          for (Map.Entry<MarketplaceType, List<EtlSourceExecution>> entry : byMarketplace.entrySet()) {
            MarketplaceType marketplace = entry.getKey();
            List<EtlSourceExecution> executions = entry.getValue();

            MarketplaceExecutionPlan plan = new MarketplaceExecutionPlan(
                requestId,
                request.accountId(),
                event,
                marketplace,
                executions
            );

            MarketplaceKey key = new MarketplaceKey(
                requestId,
                request.accountId(),
                event,
                marketplace
            );

            MarketplaceExecutionPlan previous = plans.put(key, plan);
            if (previous != null) {
              log.warn("Execution plan overwritten: key={}", key);
            }

            startStage(plan, plan.currentOrder);
          }

          return null;
        }, endpoint -> endpoint.requiresReply(false))
        .get();
  }

  @Bean
  public IntegrationFlow orchestratorSourceFailListener() {
    return IntegrationFlow
        .from(CH_ETL_PLAN_FAILED)
        .handle(String.class, (payload, headers) -> {
          EarlySourceFailureContext ctx = EarlySourceFailureContext.fromHeaders(headers);

          if (ctx.isComplete()) {
            markSourceFailedBeforeSnapshot(
                ctx.requestId(),
                ctx.accountId(),
                ctx.event(),
                ctx.marketplace(),
                ctx.sourceId()
            );
          } else {
            log.warn(
                "Plan-failure event received with incomplete context; headers={}",
                headers
            );
          }

          return null;
        }, e -> e.requiresReply(false))
        .get();
  }

  private void startStage(MarketplaceExecutionPlan plan, int order) {
    List<EtlSourceExecution> list = plan.stages.get(order);
    if (list == null || list.isEmpty()) {
      return;
    }

    for (EtlSourceExecution exec : list) {
      Map<String, Object> headers = new LinkedHashMap<>();
      headers.put(HDR_ETL_REQUEST_ID, plan.requestId);
      headers.put(HDR_ETL_ACCOUNT_ID, plan.accountId);
      headers.put(HDR_ETL_EVENT, plan.event.name());
      headers.put(HDR_ETL_DATE_FROM, exec.from());
      headers.put(HDR_ETL_DATE_TO, exec.to());
      headers.put(HDR_ETL_SOURCE_ID, exec.sourceId());
      headers.put(HDR_ETL_SOURCE_MP, exec.marketplace());

      GenericMessage<EtlSourceExecution> message = new GenericMessage<>(exec, headers);
      boolean sent = ingestChannel.send(message);
      if (!sent) {
        log.warn(
            "Failed to send EtlSourceExecution: requestId={}, marketplace={}, order={}, sourceId={}",
            plan.requestId,
            plan.marketplace,
            order,
            exec.sourceId()
        );
      }
    }
  }

  private void registerMaterializationJob(
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to,
      int expectedSnapshots
  ) {
    MaterializationJobKey key = new MaterializationJobKey(requestId, accountId, event);
    MaterializationJob job = new MaterializationJob(
        requestId,
        accountId,
        event,
        from,
        to,
        expectedSnapshots
    );

    MaterializationJob previous = materializationJobs.put(key, job);
    if (previous != null) {
      log.warn(
          "MaterializationJob overwritten: key={}, previousExpected={}, newExpected={}",
          key,
          previous.expectedSnapshots,
          expectedSnapshots
      );
    } else {
      log.debug(
          "MaterializationJob registered: key={}, expectedSnapshots={}",
          key,
          expectedSnapshots
      );
    }
  }

  private Map<String, Object> buildAcceptedResponseBody(
      EtlRunRequest request,
      Map<String, Object> headers
  ) {
    Map<String, Object> responseBody = new LinkedHashMap<>();
    responseBody.put("status", "accepted");
    responseBody.put("event", request.event());
    responseBody.put("accountId", request.accountId());
    responseBody.put("requestId", headers.get(HDR_ETL_REQUEST_ID));
    return responseBody;
  }

  private void validateRunRequest(EtlRunRequest request) {
    List<String> missingFields = Stream.of(
            new RequiredField("accountId", request.accountId()),
            new RequiredField("event", request.event()),
            new RequiredField("from", request.from()),
            new RequiredField("to", request.to())
        )
        .filter(field -> field.value() == null)
        .map(RequiredField::name)
        .toList();

    if (!missingFields.isEmpty()) {
      throw new AppException(
          MessageCodes.ETL_REQUEST_INVALID,
          String.join(", ", missingFields)
      );
    }

    if (request.from().isAfter(request.to())) {
      throw new AppException(
          MessageCodes.ETL_REQUEST_INVALID,
          "'from' must be <= 'to'"
      );
    }
  }

  private List<EtlSourceExecution> buildSourceExecutions(
      EtlRunRequest request,
      MarketplaceEvent event
  ) {
    List<EtlSourceExecution> sourceExecutions = Stream.of(MarketplaceType.values())
        .map(marketplace -> Map.entry(
            marketplace,
            etlSourceRegistry.findSources(event, marketplace)
        ))
        .filter(entry -> !entry.getValue().isEmpty())
        .flatMap(entry -> {
          MarketplaceType marketplace = entry.getKey();
          List<RegisteredSource> sources = entry.getValue();
          return sources.stream()
              .map(source -> new EtlSourceExecution(
                  source.sourceId(),
                  source.event(),
                  marketplace,
                  request.accountId(),
                  request.from(),
                  request.to(),
                  source.order(),
                  source.source()
              ));
        })
        .toList();

    if (sourceExecutions.isEmpty()) {
      throw new AppException(MessageCodes.ETL_EVENT_SOURCES_MISSING, event.name());
    }

    return sourceExecutions;
  }
}
