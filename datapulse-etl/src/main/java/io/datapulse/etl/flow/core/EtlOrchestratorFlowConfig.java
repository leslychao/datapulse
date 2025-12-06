package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;
import static io.datapulse.etl.config.EtlExecutionRabbitConfig.DEFAULT_WAIT_TTL_MILLIS;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_RUN_CORE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EXECUTION_GROUP_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_ORCHESTRATION_COMMAND;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.core.service.EtlSyncAuditService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.flow.advice.EtlAbstractRequestHandlerAdvice;
import io.datapulse.etl.flow.core.handler.EtlIngestErrorHandler;
import io.datapulse.etl.service.EtlMaterializationService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
import org.apache.commons.collections4.CollectionUtils;
import org.checkerframework.checker.units.qual.h;
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

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlSyncAuditService etlSyncAuditService;
  private final EtlMaterializationService etlMaterializationService;

  private record MarketplacePlan(
      MarketplaceType marketplace,
      List<EtlSourceExecution> executions
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
        .handle(
            OrchestrationCommand.class,
            (command, headers) -> Map.of(
                "requestId", Objects.requireNonNull(headers.get(HDR_ETL_REQUEST_ID, String.class)),
                "accountId", command.accountId(),
                "event", command.event().name()
            ),
            endpoint -> endpoint.requiresReply(true)
        )
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
        dateTo,
        List.of()
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
        Object payload = message.getPayload();
        if (!(payload instanceof EtlSourceExecution execution)) {
          return callback.execute();
        }
        try {
          callback.execute();
          long rowsCount = 0L;
          return new ExecutionOutcome(
              execution.requestId(),
              execution.accountId(),
              execution.sourceId(),
              execution.marketplace(),
              execution.event(),
              IngestStatus.SUCCESS,
              rowsCount,
              null,
              null
          );
        } catch (Exception ex) {
          Throwable cause = unwrapProcessingError(ex);
          return ingestErrorHandler.handleIngestError(
              cause,
              message
          );
        }
      }
    };
  }

  @Bean
  public IntegrationFlow etlRunCoreFlow(
      TaskExecutor etlOrchestrateExecutor,
      Advice etlIngestExecutionAdvice
  ) {
    return IntegrationFlow
        .from(CH_ETL_RUN_CORE)
        .enrichHeaders(enricher -> enricher
            .headerFunction(
                HDR_ETL_ORCHESTRATION_COMMAND,
                Message::getPayload
            )
        )
        .transform(OrchestrationCommand.class, this::buildMarketplacePlans)
        .split()
        .channel(c -> c.executor(etlOrchestrateExecutor))
        .transform(MarketplacePlan.class, MarketplacePlan::executions)
        .split()
        .gateway(
            CH_ETL_INGEST,
            gateway -> gateway
                .requestTimeout(0L)
                .replyTimeout(0L)
                .advice(etlIngestExecutionAdvice)
        )
        .enrichHeaders(h -> h
            .headerExpression(
                HDR_ETL_EXECUTION_GROUP_ID,
                "payload.requestId + ':' + payload.accountId + ':' + payload.event.name()"
            )
        )
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message ->
                message.getHeaders().get(HDR_ETL_EXECUTION_GROUP_ID, String.class)
            )
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

  private List<MarketplacePlan> buildMarketplacePlans(OrchestrationCommand command) {
    long accountId = command.accountId();
    MarketplaceEvent event = command.event();

    List<RegisteredSource> registeredSources = etlSourceRegistry.getSources(event);
    if (CollectionUtils.isEmpty(registeredSources)) {
      throw new AppException(ETL_REQUEST_INVALID, "No ETL sources registered for event " + event);
    }

    Set<String> filteredSourceIds = CollectionUtils.isEmpty(command.sourceIds())
        ? Set.of()
        : new LinkedHashSet<>(command.sourceIds());

    Set<MarketplaceType> activeMarketplaces = accountConnectionService.getActiveMarketplacesByAccountId(
        accountId);

    List<EtlSourceExecution> executions = registeredSources.stream()
        .filter(src -> filteredSourceIds.isEmpty() || filteredSourceIds.contains(src.sourceId()))
        .filter(src -> {
          boolean active = activeMarketplaces.contains(src.marketplace());
          if (!active) {
            log.info(
                "Skip source due to no active connection: accountId={}, marketplace={}, sourceId={}",
                accountId, src.marketplace(), src.sourceId()
            );
          }
          return active;
        })
        .map(src -> new EtlSourceExecution(
            command.requestId(),
            src.sourceId(),
            event,
            src.marketplace(),
            accountId,
            command.dateFrom(),
            command.dateTo(),
            src.order(),
            src.source(),
            src.rawTable()
        ))
        .toList();

    Map<MarketplaceType, List<EtlSourceExecution>> byMarketplace = executions.stream()
        .collect(Collectors.groupingBy(
            EtlSourceExecution::marketplace,
            LinkedHashMap::new,
            Collectors.toList()
        ));

    List<MarketplacePlan> plans = byMarketplace.entrySet().stream()
        .map(e -> new MarketplacePlan(e.getKey(), e.getValue()))
        .toList();

    log.info(
        "Orchestration plan built: accountId={}, event={}, sources={}",
        accountId,
        event,
        plans
    );

    return plans;
  }

  private ExecutionAggregationResult buildExecutionAggregationResult(MessageGroup group) {
    var messages = group.getMessages();
    if (messages.isEmpty()) {
      throw new IllegalStateException("Empty message group in ETL aggregation");
    }

    Message<?> sample = messages.iterator().next();

    OrchestrationCommand command =
        sample.getHeaders().get(HDR_ETL_ORCHESTRATION_COMMAND, OrchestrationCommand.class);

    if (command == null) {
      throw new IllegalStateException(
          "Missing OrchestrationCommand header in ETL aggregation group"
      );
    }

    List<ExecutionOutcome> outcomes = group
        .streamMessages()
        .map(Message::getPayload)
        .map(ExecutionOutcome.class::cast)
        .toList();

    return new ExecutionAggregationResult(
        command.requestId(),
        command.accountId(),
        command.event(),
        command.dateFrom(),
        command.dateTo(),
        outcomes
    );
  }

  private Object finalizeExecutionGroup(
      ExecutionAggregationResult aggregation,
      MessageHeaders messageHeaders
  ) {
    List<ExecutionOutcome> outcomes = aggregation.outcomes();
    updateAudit(aggregation, outcomes);
    boolean hasSuccess = outcomes.stream().anyMatch(o -> o.status() == IngestStatus.SUCCESS);
    boolean hasWaitingRetry = outcomes.stream()
        .anyMatch(o -> o.status() == IngestStatus.WAITING_RETRY);
    if (hasWaitingRetry) {
      scheduleWaitRetry(aggregation, outcomes);
    } else if (hasSuccess) {
      startMaterialization(aggregation);
    }

    return null;
  }

  private void updateAudit(
      ExecutionAggregationResult aggregation,
      List<ExecutionOutcome> outcomes
  ) {
    List<String> failedSourceIds = outcomes.stream()
        .filter(o -> o.status() == IngestStatus.FAILED)
        .map(ExecutionOutcome::sourceId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    String failedSourcesSummary = failedSourceIds.isEmpty()
        ? null
        : String.join(",", failedSourceIds);
    System.out.println(failedSourcesSummary);
  }

  private void startMaterialization(ExecutionAggregationResult aggregation) {
    try {
      etlMaterializationService.materialize(
          aggregation.accountId(),
          aggregation.event(),
          aggregation.dateFrom(),
          aggregation.dateTo(),
          aggregation.requestId()
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

    OrchestrationCommand retryCommand = new OrchestrationCommand(
        aggregation.requestId(),
        aggregation.accountId(),
        aggregation.event(),
        aggregation.dateFrom(),
        aggregation.dateTo(),
        retrySourceIds
    );

    etlExecutionRabbitTemplate.convertAndSend(
        EXCHANGE_EXECUTION_DLX,
        ROUTING_KEY_EXECUTION_WAIT,
        retryCommand,
        message -> {
          message.getMessageProperties().setExpiration(Long.toString(ttlMillis));
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
