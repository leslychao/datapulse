package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;
import static io.datapulse.etl.config.EtlExecutionRabbitConfig.DEFAULT_WAIT_TTL_MILLIS;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_RUN_CORE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_RAW_TABLE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_RETRY_SOURCE_IDS;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_SOURCE_MARKETPLACE;

import io.datapulse.core.i18n.I18nMessageService;
import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.core.service.EtlSyncAuditService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.flow.advice.EtlAbstractRequestHandlerAdvice;
import io.datapulse.etl.flow.core.handler.EtlIngestErrorHandler;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.etl.service.EtlMaterializationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlOrchestratorFlowConfig {

  private static final String HDR_ORCHESTRATION_COMMAND = "ETL_ORCHESTRATION_COMMAND";

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlSyncAuditService etlSyncAuditService;
  private final EtlMaterializationService etlMaterializationService;
  private final RawBatchInsertJdbcRepository repository;

  private record MarketplacePlan(
      MarketplaceType marketplace,
      List<EtlSourceExecution> executions
  ) {

  }

  private record ExecutionAggregationResult(
      String requestId,
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo,
      List<String> expectedSourceIds,
      List<ExecutionOutcome> outcomes
  ) {

  }

  @Bean(name = CH_ETL_RUN_CORE)
  public MessageChannel etlRunCoreChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_ETL_ORCHESTRATE)
  public MessageChannel etlOrchestrateChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_ETL_ORCHESTRATION_RESULT)
  public MessageChannel etlOrchestrationResultChannel() {
    return new DirectChannel();
  }

  @Bean("etlOrchestrateExecutor")
  public TaskExecutor etlOrchestrateExecutor(
      @Value("${etl.orchestrate.core-pool-size:4}") int corePoolSize,
      @Value("${etl.orchestrate.max-pool-size:8}") int maxPoolSize
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("etl-orchestrate-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean
  public IntegrationFlow etlHttpInboundFlow() {
    return IntegrationFlow
        .from(
            Http.inboundGateway("/api/etl/run")
                .requestPayloadType(EtlRunRequest.class)
                .statusCodeFunction(message -> HttpStatus.ACCEPTED)
        )
        .transform(EtlRunRequest.class, this::toOrchestrationCommand)
        .wireTap(flow -> flow
            .handle(
                Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                    .exchangeName(EXCHANGE_EXECUTION)
                    .routingKey(ROUTING_KEY_EXECUTION),
                endpoint -> endpoint.requiresReply(false)
            )
        )
        .transform(OrchestrationCommand.class, command -> Map.of(
            "requestId", command.requestId(),
            "accountId", command.accountId(),
            "event", command.event().name()
        ))
        .get();
  }

  private OrchestrationCommand toOrchestrationCommand(EtlRunRequest request) {
    if (request == null) {
      throw new AppException(ETL_REQUEST_INVALID);
    }
    if (request.accountId() == null || request.event() == null) {
      throw new AppException(ETL_REQUEST_INVALID);
    }

    LocalDate dateFrom = request.dateFrom();
    LocalDate dateTo = request.dateTo();
    if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
      throw new AppException(ETL_REQUEST_INVALID);
    }

    String requestId = UUID.randomUUID().toString();

    return new OrchestrationCommand(
        requestId,
        request.accountId(),
        MarketplaceEvent.fromString(request.event()),
        dateFrom,
        dateTo
    );
  }

  @Bean
  public IntegrationFlow etlExecutionInboundFlow(
      ConnectionFactory connectionFactory,
      MessageConverter etlExecutionMessageConverter
  ) {
    return IntegrationFlow
        .from(
            Amqp.inboundAdapter(connectionFactory, QUEUE_EXECUTION)
                .messageConverter(etlExecutionMessageConverter)
                .configureContainer(container -> container
                    .concurrentConsumers(1)
                    .maxConcurrentConsumers(1)
                    .prefetchCount(1)
                    .defaultRequeueRejected(false)
                )
        )
        .channel(CH_ETL_RUN_CORE)
        .get();
  }

  @Bean
  public Advice etlIngestExecutionAdvice(EtlIngestErrorHandler ingestErrorHandler) {
    return new EtlAbstractRequestHandlerAdvice() {

      @Override
      protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
        try {
          return callback.execute();
        } catch (Exception ex) {
          Throwable cause = unwrapProcessingError(ex);
          return ingestErrorHandler.handleIngestError(cause, message);
        }
      }
    };
  }

  @Bean
  public IntegrationFlow etlRunCoreFlow(
      TaskExecutor etlOrchestrateExecutor,
      Advice etlIngestExecutionErrorAdvice
  ) {
    return IntegrationFlow
        .from(CH_ETL_RUN_CORE)
        .enrichHeaders(e -> e
            .headerFunction(HDR_ETL_REQUEST_ID,
                (Message<OrchestrationCommand> m) -> m.getPayload().requestId()
            )
            .headerFunction(HDR_ETL_ACCOUNT_ID,
                (Message<OrchestrationCommand> m) -> m.getPayload().accountId()
            )
            .headerFunction(HDR_ETL_EVENT,
                (Message<OrchestrationCommand> m) -> m.getPayload().event()
            )
            .headerFunction(HDR_ETL_DATE_FROM,
                (Message<OrchestrationCommand> m) -> m.getPayload().dateFrom()
            )
            .headerFunction(HDR_ETL_DATE_TO,
                (Message<OrchestrationCommand> m) -> m.getPayload().dateTo()
            )
            .headerFunction(HDR_ORCHESTRATION_COMMAND,
                (Message<OrchestrationCommand> m) -> m.getPayload()
            )
        )
        .transform(Message.class, this::buildMarketplacePlans)
        .split()
        .channel(c -> c.executor(etlOrchestrateExecutor))
        .log(LoggingHandler.Level.INFO, message ->
            "Building executions for marketplace=" +
                message.getPayload(MarketplacePlan.class).marketplace()
        )
        .transform(MarketplacePlan.class, MarketplacePlan::executions)
        .split()
        .gateway(
            CH_ETL_INGEST,
            gateway -> gateway
                .requestTimeout(0L)
                .replyTimeout(0L)
                .requiresReply(false)
                .advice(etlIngestExecutionErrorAdvice)
        )
        .handle(
            EtlSourceExecution.class,
            (execution, headersMap) -> {
              MessageHeaders headers = new MessageHeaders(headersMap);
              String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);

              long totalRows = repository.countByRequestId(
                  execution.rawTable(),
                  requestId
              );

              return totalRows;
            }
        )
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message -> {
              MessageHeaders headers = message.getHeaders();
              String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
              Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
              MarketplaceEvent event = headers.get(HDR_ETL_EVENT, MarketplaceEvent.class);
              if (requestId == null || accountId == null || event == null) {
                throw new IllegalStateException(
                    "Missing correlation headers for ETL aggregation: requestId/accountId/event"
                );
              }
              return requestId + ":" + accountId + ":" + event.name();
            })
            .outputProcessor(this::buildExecutionAggregationResult)
            .expireGroupsUponCompletion(true)
        )
        .channel(CH_ETL_ORCHESTRATION_RESULT)
        .handle(
            ExecutionAggregationResult.class,
            this::finalizeExecutionGroup,
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  private List<MarketplacePlan> buildMarketplacePlans(Message<OrchestrationCommand> message) {
    OrchestrationCommand command = message.getPayload();
    MessageHeaders headers = message.getHeaders();

    long accountId = command.accountId();
    MarketplaceEvent event = command.event();
    LocalDate from = command.dateFrom();
    LocalDate to = command.dateTo();

    List<RegisteredSource> registeredSources = etlSourceRegistry.getSources(event);

    if (registeredSources.isEmpty()) {
      throw new AppException(ETL_REQUEST_INVALID, "No ETL sources registered for event " + event);
    }

    Set<String> retrySourceIds = resolveRetrySourceIds(headers);

    List<RegisteredSource> filteredSources = registeredSources
        .stream()
        .filter(source -> shouldExecuteSource(source, retrySourceIds))
        .toList();

    if (filteredSources.isEmpty()) {
      log.info(
          "No sources to execute after retry filtering: requestId={}, accountId={}, event={}",
          command.requestId(),
          command.accountId(),
          command.event()
      );
    }

    List<EtlSourceExecution> executions = new ArrayList<>();
    for (RegisteredSource source : filteredSources) {
      if (!accountConnectionService.hasActiveConnection(accountId, source.marketplace())) {
        log.info(
            "Skip source due to no active connection: accountId={}, marketplace={}, sourceId={}",
            accountId,
            source.marketplace(),
            source.sourceId()
        );
        continue;
      }

      EtlSourceExecution execution = new EtlSourceExecution(
          command.requestId(),
          accountId,
          event,
          source.marketplace(),
          source.sourceId(),
          source.rawTable(),
          from,
          to,
          source.source()
      );
      executions.add(execution);
    }

    Map<MarketplaceType, List<EtlSourceExecution>> byMarketplace = executions
        .stream()
        .collect(Collectors.groupingBy(
            EtlSourceExecution::marketplace,
            LinkedHashSet::new,
            Collectors.toList()
        ));

    List<MarketplacePlan> plans = new ArrayList<>();
    for (Map.Entry<MarketplaceType, List<EtlSourceExecution>> entry : byMarketplace.entrySet()) {
      plans.add(new MarketplacePlan(entry.getKey(), entry.getValue()));
    }

    List<String> sourceIds = executions
        .stream()
        .map(EtlSourceExecution::sourceId)
        .distinct()
        .toList();

    log.info(
        "Orchestration plan built: requestId={}, accountId={}, event={}, sources={}",
        command.requestId(),
        accountId,
        event,
        sourceIds
    );

    return plans;
  }

  private Set<String> resolveRetrySourceIds(MessageHeaders headers) {
    String[] retryArray = headers.get(HDR_ETL_RETRY_SOURCE_IDS, String[].class);
    if (retryArray == null || retryArray.length == 0) {
      return Set.of();
    }
    Set<String> result = new LinkedHashSet<>();
    for (String id : retryArray) {
      if (id != null && !id.isBlank()) {
        result.add(id);
      }
    }
    return result;
  }

  private boolean shouldExecuteSource(RegisteredSource source, Set<String> retrySourceIds) {
    if (retrySourceIds.isEmpty()) {
      return true;
    }
    return retrySourceIds.contains(source.sourceId());
  }

  // ==========================
  //  Агрегация результатов ExecutionOutcome → ExecutionAggregationResult
  // ==========================

  private ExecutionAggregationResult buildExecutionAggregationResult(MessageGroup group) {
    Message<?> sample = group.getOne();
    if (sample == null) {
      throw new IllegalStateException("Empty message group in ETL aggregation");
    }

    MessageHeaders headers = sample.getHeaders();

    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    MarketplaceEvent event = headers.get(HDR_ETL_EVENT, MarketplaceEvent.class);
    LocalDate dateFrom = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
    LocalDate dateTo = headers.get(HDR_ETL_DATE_TO, LocalDate.class);

    if (requestId == null || accountId == null || event == null || dateFrom == null
        || dateTo == null) {
      throw new IllegalStateException("Missing required headers for ExecutionAggregationResult");
    }

    List<ExecutionOutcome> outcomes = group
        .getMessages()
        .stream()
        .map(Message::getPayload)
        .filter(ExecutionOutcome.class::isInstance)
        .map(ExecutionOutcome.class::cast)
        .toList();

    List<String> expectedSourceIds = outcomes
        .stream()
        .map(ExecutionOutcome::sourceId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    return new ExecutionAggregationResult(
        requestId,
        accountId,
        event,
        dateFrom,
        dateTo,
        expectedSourceIds,
        outcomes
    );
  }

  // ==========================
  //  Финальное решение по группе: audit + materialization + WAIT (ретрай)
  // ==========================

  private Object finalizeExecutionGroup(
      ExecutionAggregationResult aggregation,
      Map<String, Object> headersMap
  ) {
    List<ExecutionOutcome> outcomes = aggregation.outcomes();

    boolean hasSuccess = outcomes.stream().anyMatch(o -> o.status() == IngestStatus.SUCCESS);
    boolean hasWaitingRetry = outcomes.stream()
        .anyMatch(o -> o.status() == IngestStatus.WAITING_RETRY);
    boolean hasFailed = outcomes.stream().anyMatch(o -> o.status() == IngestStatus.FAILED);

    SyncStatus eventStatus;
    if (hasWaitingRetry) {
      eventStatus = SyncStatus.IN_PROGRESS;
    } else if (hasFailed && hasSuccess) {
      eventStatus = SyncStatus.PARTIAL_SUCCESS;
    } else if (hasFailed) {
      eventStatus = SyncStatus.FAILED;
    } else if (hasSuccess) {
      eventStatus = SyncStatus.SUCCESS;
    } else {
      eventStatus = SyncStatus.NO_DATA;
    }

    updateAudit(aggregation, eventStatus, outcomes);

    if (hasWaitingRetry) {
      scheduleWaitRetry(aggregation, outcomes);
    } else if (hasSuccess) {
      startMaterialization(aggregation);
    }

    return null;
  }

  private void updateAudit(
      ExecutionAggregationResult aggregation,
      SyncStatus eventStatus,
      List<ExecutionOutcome> outcomes
  ) {
    // TODO: сюда вставляется твоя реальная логика обновления аудита через etlSyncAuditService.
    log.info(
        "ETL event aggregation completed: requestId={}, accountId={}, event={}, status={}, outcomes={}",
        aggregation.requestId(),
        aggregation.accountId(),
        aggregation.event(),
        eventStatus,
        outcomes.size()
    );
  }

  private void startMaterialization(ExecutionAggregationResult aggregation) {
    try {
      etlMaterializationService.materialize(
          aggregation.requestId(),
          aggregation.accountId(),
          aggregation.event(),
          aggregation.dateFrom(),
          aggregation.dateTo()
      );
    } catch (Exception ex) {
      log.error(
          "Materialization failed: requestId={}, accountId={}, event={}",
          aggregation.requestId(),
          aggregation.accountId(),
          aggregation.event(),
          ex
      );
    }
  }

  // ==========================
  //  Планирование WAIT-ретрая в Rabbit
  // ==========================

  private void scheduleWaitRetry(
      ExecutionAggregationResult aggregation,
      List<ExecutionOutcome> outcomes
  ) {
    List<String> retrySourceIds = outcomes
        .stream()
        .filter(o -> o.status() == IngestStatus.WAITING_RETRY)
        .map(ExecutionOutcome::sourceId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    if (retrySourceIds.isEmpty()) {
      return;
    }

    long ttlMillis = outcomes
        .stream()
        .filter(o -> o.status() == IngestStatus.WAITING_RETRY)
        .map(ExecutionOutcome::retryAfterMillis)
        .filter(Objects::nonNull)
        .filter(millis -> millis > 0L)
        .min(Long::compareTo)
        .orElse(DEFAULT_WAIT_TTL_MILLIS);

    OrchestrationCommand command = new OrchestrationCommand(
        aggregation.requestId(),
        aggregation.accountId(),
        aggregation.event(),
        aggregation.dateFrom(),
        aggregation.dateTo()
    );

    etlExecutionRabbitTemplate.convertAndSend(
        EXCHANGE_EXECUTION_DLX,
        ROUTING_KEY_EXECUTION_WAIT,
        command,
        message -> {
          message.getMessageProperties().setExpiration(Long.toString(ttlMillis));

          message.getMessageProperties()
              .setHeader(HDR_ETL_REQUEST_ID, aggregation.requestId());
          message.getMessageProperties()
              .setHeader(HDR_ETL_ACCOUNT_ID, aggregation.accountId());
          message.getMessageProperties()
              .setHeader(HDR_ETL_EVENT, aggregation.event().name());
          message.getMessageProperties()
              .setHeader(HDR_ETL_DATE_FROM, aggregation.dateFrom().toString());
          message.getMessageProperties()
              .setHeader(HDR_ETL_DATE_TO, aggregation.dateTo().toString());
          // ВАЖНО: HDR_ETL_RETRY_SOURCE_IDS как String[], чтобы не ловить ошибку типов.
          message.getMessageProperties()
              .setHeader(HDR_ETL_RETRY_SOURCE_IDS, retrySourceIds.toArray(new String[0]));

          return message;
        }
    );

    log.info(
        "Scheduled WAIT retry: requestId={}, accountId={}, event={}, retrySourceIds={}, ttlMillis={}",
        aggregation.requestId(),
        aggregation.accountId(),
        aggregation.event(),
        retrySourceIds,
        ttlMillis
    );
  }
}
