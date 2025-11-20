package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ERRORS;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_EVENT_INGEST_COMPLETED;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EXPECTED_SNAPSHOTS;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_RAW_TABLE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MP;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.file.SnapshotCommitBarrier;
import io.datapulse.etl.file.SnapshotCommitBarrier.SnapshotCompletionEvent;
import io.datapulse.etl.flow.EtlSnapshotIngestionFlowConfig.EtlSourceExecution;
import io.datapulse.etl.route.EtlSourceRegistry;
import io.datapulse.etl.route.EtlSourceRegistry.RegisteredSource;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
public class EtlOrchestratorFlowConfig {

  private final EtlSourceRegistry etlSourceRegistry;
  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final MessageChannel eventIngestCompletedChannel;

  public EtlOrchestratorFlowConfig(
      EtlSourceRegistry etlSourceRegistry,
      SnapshotCommitBarrier snapshotCommitBarrier,
      @Qualifier(CH_ETL_EVENT_INGEST_COMPLETED)
      MessageChannel eventIngestCompletedChannel
  ) {
    this.etlSourceRegistry = etlSourceRegistry;
    this.snapshotCommitBarrier = snapshotCommitBarrier;
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

  @PostConstruct
  public void registerSnapshotCompletionListener() {
    snapshotCommitBarrier.registerListener(this::onSnapshotCompleted);
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
      return;
    }

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

  @Bean("etlMarketplaceExecutor")
  public TaskExecutor etlMarketplaceExecutor(
      @Value("${etl.marketplace.core-pool-size:4}") int corePoolSize,
      @Value("${etl.marketplace.max-pool-size:16}") int maxPoolSize,
      @Value("${etl.marketplace.queue-capacity:200}") int queueCapacity
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("etl-marketplace-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
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
  public IntegrationFlow etlOrchestrateFlow(TaskExecutor etlMarketplaceExecutor) {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATE)
        .enrichHeaders(headers ->
            headers.header(org.springframework.messaging.MessageHeaders.ERROR_CHANNEL,
                CH_ETL_ERRORS,
                true
            ))
        .headerFilter(org.springframework.messaging.MessageHeaders.REPLY_CHANNEL)
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

          return groupExecutionsByMarketplace(sourceExecutions);
        })
        .split()
        .channel(channelSpec -> channelSpec.executor(etlMarketplaceExecutor))
        .split()
        .enrichHeaders(headers -> headers
            .headerFunction(HDR_ETL_SOURCE_ID, message -> {
              EtlSourceExecution exec = (EtlSourceExecution) message.getPayload();
              return exec.sourceId();
            })
            .headerFunction(HDR_ETL_SOURCE_MP, message -> {
              EtlSourceExecution exec = (EtlSourceExecution) message.getPayload();
              return exec.marketplace();
            })
            .headerFunction(HDR_ETL_ACCOUNT_ID, message -> {
              EtlSourceExecution exec = (EtlSourceExecution) message.getPayload();
              return exec.accountId();
            })
            .headerFunction(HDR_ETL_EVENT, message -> {
              EtlSourceExecution exec = (EtlSourceExecution) message.getPayload();
              return exec.event().name();
            })
            .headerFunction(HDR_ETL_DATE_FROM, message -> {
              EtlSourceExecution exec = (EtlSourceExecution) message.getPayload();
              return exec.from();
            })
            .headerFunction(HDR_ETL_DATE_TO, message -> {
              EtlSourceExecution exec = (EtlSourceExecution) message.getPayload();
              return exec.to();
            })
            .headerFunction(HDR_ETL_RAW_TABLE, message -> {
              EtlSourceExecution exec = (EtlSourceExecution) message.getPayload();
              return exec.rawTable();
            })
        )
        .channel(CH_ETL_INGEST)
        .get();
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

  private List<List<EtlSourceExecution>> groupExecutionsByMarketplace(
      List<EtlSourceExecution> sourceExecutions
  ) {
    return sourceExecutions.stream()
        .collect(java.util.stream.Collectors.groupingBy(EtlSourceExecution::marketplace))
        .values()
        .stream()
        .toList();
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
                  source.rawTable(),
                  request.accountId(),
                  request.from(),
                  request.to(),
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
