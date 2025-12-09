package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_AGGREGATION_EMPTY_GROUP;
import static io.datapulse.domain.MessageCodes.ETL_EVENT_SOURCES_MISSING;
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
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.etl.service.EtlMaterializationService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
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

  private static final String HDR_ETL_EXPECTED_EXECUTIONS = "ETL_EXPECTED_EXECUTIONS";

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlSyncAuditService etlSyncAuditService;
  private final EtlMaterializationService etlMaterializationService;
  private final EtlOrchestrationCommandFactory etlOrchestrationCommandFactory;

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
        .transform(
            EtlRunRequest.class,
            etlOrchestrationCommandFactory::toCommand
        )
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
                "requestId", command.requestId(),
                "accountId", command.accountId(),
                "event", command.event().name()
            ),
            endpoint -> endpoint.requiresReply(true)
        )
        .get();
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
  public Advice etlIngestExecutionAdvice(
      EtlIngestErrorHandler ingestErrorHandler,
      RawBatchInsertJdbcRepository rawBatchInsertJdbcRepository
  ) {
    return new EtlAbstractRequestHandlerAdvice() {
      @Override
      protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
        EtlSourceExecution execution = (EtlSourceExecution) message.getPayload();

        try {
          callback.execute();

          long rowsCount = rawBatchInsertJdbcRepository.countByRequestId(
              execution.rawTable(),
              execution.requestId()
          );

          IngestStatus status =
              rowsCount > 0L ? IngestStatus.SUCCESS : IngestStatus.NO_DATA;

          return new ExecutionOutcome(
              execution.requestId(),
              execution.accountId(),
              execution.sourceId(),
              execution.marketplace(),
              execution.event(),
              execution.dateFrom(),
              execution.dateTo(),
              status,
              rowsCount,
              null,
              null
          );
        } catch (Exception ex) {
          Throwable cause = unwrapProcessingError(ex);
          return ingestErrorHandler.handleIngestError(cause, execution);
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
        .transform(OrchestrationCommand.class, this::buildMarketplacePlans)
        .enrichHeaders(h -> h.headerFunction(
            HDR_ETL_EXPECTED_EXECUTIONS,
            (Message<List<MarketplacePlan>> msg) ->
                msg.getPayload()
                    .stream()
                    .mapToInt(plan -> plan.executions().size())
                    .sum()
        ))
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
        .wireTap(flow -> flow
            .filter(
                ExecutionOutcome.class,
                outcome -> outcome.status() == IngestStatus.WAITING_RETRY
            )
            .enrichHeaders(h -> h.headerFunction(
                AmqpHeaders.EXPIRATION,
                (Message<ExecutionOutcome> msg) -> {
                  ExecutionOutcome outcome = msg.getPayload();
                  Long retryAfter = outcome.retryAfterMillis();
                  long ttl = (retryAfter != null && retryAfter > 0L)
                      ? retryAfter
                      : DEFAULT_WAIT_TTL_MILLIS;
                  return Long.toString(ttl);
                }
            ))
            .handle(
                ExecutionOutcome.class,
                (outcome, headers) -> new OrchestrationCommand(
                    outcome.requestId(),
                    outcome.accountId(),
                    outcome.event(),
                    outcome.dateFrom(),
                    outcome.dateTo(),
                    List.of(outcome.sourceId())
                )
            )
            .handle(
                Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                    .exchangeName(EXCHANGE_EXECUTION_DLX)
                    .routingKey(ROUTING_KEY_EXECUTION_WAIT),
                endpoint -> endpoint.requiresReply(false)
            )
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
            .releaseStrategy(this::isAggregationComplete)
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
    if (registeredSources.isEmpty()) {
      throw new AppException(ETL_EVENT_SOURCES_MISSING, event);
    }

    Set<String> filteredSourceIds = command.sourceIds().isEmpty()
        ? Set.of()
        : new LinkedHashSet<>(command.sourceIds());

    Set<MarketplaceType> activeMarketplaces = accountConnectionService
        .getActiveMarketplacesByAccountId(accountId);

    List<EtlSourceExecution> executions = registeredSources.stream()
        .filter(src -> filteredSourceIds.isEmpty() || filteredSourceIds.contains(src.sourceId()))
        .filter(src -> {
          boolean active = activeMarketplaces.contains(src.marketplace());
          if (!active) {
            log.info(
                "Skip source due dateTo no active connection: accountId={}, marketplace={}, sourceId={}",
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

  private boolean isAggregationComplete(MessageGroup group) {
    List<Message<?>> messages = group.getMessages().stream().toList();
    if (messages.isEmpty()) {
      return false;
    }

    Message<?> sample = messages.iterator().next();
    Integer expected = sample.getHeaders().get(HDR_ETL_EXPECTED_EXECUTIONS, Integer.class);
    if (expected == null) {
      throw new AppException(
          ETL_REQUEST_INVALID,
          HDR_ETL_EXPECTED_EXECUTIONS
      );
    }

    Map<String, List<IngestStatus>> statusesBySourceId = group.streamMessages()
        .map(Message::getPayload)
        .filter(ExecutionOutcome.class::isInstance)
        .map(ExecutionOutcome.class::cast)
        .filter(outcome -> outcome.sourceId() != null)
        .collect(Collectors.groupingBy(
            ExecutionOutcome::sourceId,
            Collectors.mapping(ExecutionOutcome::status, Collectors.toList())
        ));

    if (statusesBySourceId.isEmpty() || statusesBySourceId.size() < expected) {
      return false;
    }

    return statusesBySourceId.values().stream()
        .allMatch(statuses -> statuses.stream().anyMatch(IngestStatus::isTerminal));
  }

  private ExecutionAggregationResult buildExecutionAggregationResult(MessageGroup group) {
    List<ExecutionOutcome> outcomes = group
        .streamMessages()
        .map(Message::getPayload)
        .filter(ExecutionOutcome.class::isInstance)
        .map(ExecutionOutcome.class::cast)
        .toList();

    if (outcomes.isEmpty()) {
      throw new AppException(ETL_AGGREGATION_EMPTY_GROUP);
    }

    ExecutionOutcome sample = outcomes.iterator().next();

    return new ExecutionAggregationResult(
        sample.requestId(),
        sample.accountId(),
        sample.event(),
        sample.dateFrom(),
        sample.dateTo(),
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
    if (hasSuccess) {
      startMaterialization(aggregation);
    }
    return null;
  }

  private void updateAudit(
      ExecutionAggregationResult aggregation,
      List<ExecutionOutcome> outcomes
  ) {
    List<String> failedSourceIds = outcomes.stream()
        .filter(o -> o.status() == IngestStatus.FAILED || o.status() == IngestStatus.WAITING_RETRY)
        .map(ExecutionOutcome::sourceId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    String failedSourcesSummary = failedSourceIds.isEmpty()
        ? null
        : String.join(",", failedSourceIds);

    outcomes.forEach(executionOutcome -> {
      System.out.println("AUDIT====================: " + executionOutcome);
    });
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
}
