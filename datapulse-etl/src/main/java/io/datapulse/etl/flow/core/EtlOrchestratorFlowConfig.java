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
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_PREPARE_RAW_SCHEMA;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_RUN_CORE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EXECUTION_GROUP_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EXPECTED_EXECUTIONS;

import io.datapulse.core.service.account.AccountConnectionService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.dto.MarketplacePlan;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.dto.OrchestrationPlan;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlOrchestratorFlowConfig {

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlOrchestrationCommandFactory etlOrchestrationCommandFactory;

  @Bean(name = CH_ETL_PREPARE_RAW_SCHEMA)
  public MessageChannel etlPrepareRawSchemaChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_ETL_RUN_CORE)
  public MessageChannel etlRunCoreChannel() {
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
        .transform(EtlRunRequest.class, etlOrchestrationCommandFactory::toCommand)
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
  public IntegrationFlow etlRunCoreFlow(
      TaskExecutor etlOrchestrateExecutor,
      Advice etlIngestExecutionAdvice
  ) {
    return IntegrationFlow
        .from(CH_ETL_RUN_CORE)
        .transform(OrchestrationCommand.class, this::buildOrchestrationPlan)
        .gateway(CH_ETL_PREPARE_RAW_SCHEMA)
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_ETL_EXPECTED_EXECUTIONS,
            (Message<OrchestrationPlan> message) -> message.getPayload().expectedExecutions()
        ))
        .split(OrchestrationPlan.class, OrchestrationPlan::plans)
        .channel(channel -> channel.executor(etlOrchestrateExecutor))
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
            .enrichHeaders(headers -> headers.headerFunction(
                AmqpHeaders.EXPIRATION,
                (Message<ExecutionOutcome> message) -> {
                  ExecutionOutcome outcome = message.getPayload();
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
        .filter(ExecutionOutcome.class, outcome -> outcome.status().isTerminal())
        .enrichHeaders(headers -> headers
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
        .get();
  }

  private OrchestrationPlan buildOrchestrationPlan(OrchestrationCommand command) {
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
        .map(entry -> new MarketplacePlan(entry.getKey(), entry.getValue()))
        .toList();

    int expectedExecutions = plans.stream()
        .mapToInt(plan -> plan.executions().size())
        .sum();

    log.info(
        "Orchestration plan built: accountId={}, event={}, marketplaces={}, expectedExecutions={}",
        accountId,
        event,
        plans.stream().map(MarketplacePlan::marketplace).toList(),
        expectedExecutions
    );

    return new OrchestrationPlan(plans, expectedExecutions);
  }

  private boolean isAggregationComplete(MessageGroup group) {
    List<Message<?>> messages = group.getMessages().stream().toList();
    if (messages.isEmpty()) {
      return false;
    }

    Message<?> sample = messages.iterator().next();
    Integer expected = sample.getHeaders().get(HDR_ETL_EXPECTED_EXECUTIONS, Integer.class);
    if (expected == null) {
      throw new AppException(ETL_REQUEST_INVALID, HDR_ETL_EXPECTED_EXECUTIONS);
    }

    long distinctSources = group.streamMessages()
        .map(Message::getPayload)
        .filter(ExecutionOutcome.class::isInstance)
        .map(ExecutionOutcome.class::cast)
        .map(ExecutionOutcome::sourceId)
        .distinct()
        .count();

    return distinctSources >= expected;
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
}
