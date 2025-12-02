package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;
import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;
import static io.datapulse.etl.config.EtlExecutionRabbitConfig.DEFAULT_WAIT_TTL_MILLIS;
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
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_SOURCE_MARKETPLACE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_TOTAL_EXECUTIONS;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.core.service.EtlSyncAuditService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.IngestResult;
import io.datapulse.etl.dto.OrchestrationBundle;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.flow.advice.EtlMaterializationAdvice;
import io.datapulse.etl.flow.advice.EtlOrchestratorPlansAdvice;
import io.datapulse.etl.service.EtlMaterializationService;
import io.micrometer.common.util.StringUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlOrchestratorFlowConfig {

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlMaterializationService materializationService;
  private final EtlSyncAuditService etlSyncAuditService;
  private final EtlOrchestrationCommandFactory orchestrationCommandFactory;

  private record MarketplacePlan(
      MarketplaceType marketplace,
      List<EtlSourceExecution> executions
  ) {

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

  @Bean(name = CH_ETL_ORCHESTRATE)
  public MessageChannel etlOrchestrateChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_ETL_ORCHESTRATION_RESULT)
  public SubscribableChannel etlOrchestrationResultChannel() {
    return new PublishSubscribeChannel();
  }

  @Bean
  public IntegrationFlow etlHttpInboundFlow() {
    return IntegrationFlow
        .from(
            Http.inboundGateway("/api/etl/run")
                .requestPayloadType(EtlRunRequest.class)
                .statusCodeFunction(message -> HttpStatus.ACCEPTED)
        )
        .transform(EtlRunRequest.class, orchestrationCommandFactory::toCommand)
        .wireTap(flow -> flow
            .handle(
                Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                    .exchangeName(EXCHANGE_EXECUTION)
                    .routingKey(ROUTING_KEY_EXECUTION),
                endpoint -> endpoint.requiresReply(false)
            )
        )
        .transform(OrchestrationCommand.class, this::buildAcceptedResponse)
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
        .headerFilter("ETL_*")
        .channel(CH_ETL_RUN_CORE)
        .get();
  }

  @Bean
  public IntegrationFlow etlRunCoreFlow() {
    return IntegrationFlow
        .from(CH_ETL_RUN_CORE)
        .enrichHeaders(headers -> headers
            .headerFunction(
                HDR_ETL_REQUEST_ID,
                message -> {
                  OrchestrationCommand command = (OrchestrationCommand) message.getPayload();
                  return command.requestId();
                }
            )
            .headerFunction(
                HDR_ETL_ACCOUNT_ID,
                message -> {
                  OrchestrationCommand command = (OrchestrationCommand) message.getPayload();
                  return command.accountId();
                }
            )
            .headerFunction(
                HDR_ETL_EVENT,
                message -> {
                  OrchestrationCommand command = (OrchestrationCommand) message.getPayload();
                  return command.event().name();
                }
            )
            .headerFunction(
                HDR_ETL_DATE_FROM,
                message -> {
                  OrchestrationCommand command = (OrchestrationCommand) message.getPayload();
                  return command.from();
                }
            )
            .headerFunction(
                HDR_ETL_DATE_TO,
                message -> {
                  OrchestrationCommand command = (OrchestrationCommand) message.getPayload();
                  return command.to();
                }
            )
        )
        .log(
            LoggingHandler.Level.INFO,
            EtlOrchestratorFlowConfig.class.getName(),
            m -> "ETL run command sent to orchestrator from broker: " + m.getPayload()
        )
        .channel(CH_ETL_ORCHESTRATE)
        .get();
  }

  @Bean
  public IntegrationFlow etlOrchestratorFlow(
      TaskExecutor etlOrchestrateExecutor,
      EtlOrchestratorPlansAdvice etlOrchestratorPlansAdvice
  ) {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATE)
        .headerFilter(MessageHeaders.REPLY_CHANNEL, MessageHeaders.ERROR_CHANNEL)
        .handle(
            OrchestrationCommand.class,
            (command, headers) -> buildMarketplacePlans(command),
            endpoint -> endpoint.advice(etlOrchestratorPlansAdvice)
        )
        .route(
            Object.class,
            payload -> (payload instanceof OrchestrationBundle) ? "ERROR" : "OK",
            mapping -> mapping
                .subFlowMapping("ERROR", sf -> sf
                    .channel(CH_ETL_ORCHESTRATION_RESULT)
                )
                .subFlowMapping("OK", sf -> sf
                    .enrichHeaders(h -> h.headerFunction(
                        HDR_ETL_TOTAL_EXECUTIONS,
                        msg -> {
                          Object payload = msg.getPayload();
                          if (!(payload instanceof List<?> rawPlans)) {
                            log.warn(
                                "Unexpected payload type for orchestrator total executions calculation: {}",
                                payload.getClass().getName()
                            );
                            return 0;
                          }
                          List<MarketplacePlan> plans = rawPlans.stream()
                              .filter(MarketplacePlan.class::isInstance)
                              .map(MarketplacePlan.class::cast)
                              .toList();
                          return calculateExpectedSources(plans);
                        }
                    ))
                    .split()
                    .channel(c -> c.executor(etlOrchestrateExecutor))
                    .enrichHeaders(h -> h.headerFunction(
                        HDR_ETL_SOURCE_MARKETPLACE,
                        msg -> ((MarketplacePlan) msg.getPayload()).marketplace()
                    ))
                    .transform(MarketplacePlan.class, MarketplacePlan::executions)
                    .split()
                    .enrichHeaders(h -> h.headerFunction(
                        HDR_ETL_SOURCE_ID,
                        msg -> ((EtlSourceExecution) msg.getPayload()).sourceId()
                    ))
                    .enrichHeaders(h -> h.headerFunction(
                        HDR_ETL_RAW_TABLE,
                        msg -> ((EtlSourceExecution) msg.getPayload()).rawTable()
                    ))
                    .gateway(
                        CH_ETL_INGEST,
                        g -> g.requestTimeout(0L).replyTimeout(0L).requiresReply(true)
                    )
                    .aggregate(a -> a
                        .correlationStrategy(m ->
                            m.getHeaders().get(HDR_ETL_REQUEST_ID, String.class)
                        )
                        .releaseStrategy(this::isFullGroup)
                        .expireGroupsUponCompletion(true)
                        .outputProcessor(this::buildBundle)
                    )
                    .channel(CH_ETL_ORCHESTRATION_RESULT)
                )
        )
        .get();
  }

  private boolean isFullGroup(MessageGroup group) {
    Message<?> sampleMessage = group.getOne();
    if (sampleMessage == null) {
      log.warn("Orchestrator aggregate completed with empty group");
      return true;
    }

    MessageHeaders headers = sampleMessage.getHeaders();
    Integer expected = headers.get(HDR_ETL_TOTAL_EXECUTIONS, Integer.class);
    if (expected == null) {
      log.warn(
          "Orchestrator aggregate has no '{}' header, releasing immediately",
          HDR_ETL_TOTAL_EXECUTIONS
      );
      return true;
    }

    int currentSize = group.size();
    if (currentSize >= expected) {
      if (currentSize > expected) {
        log.warn(
            "Orchestrator aggregate group size exceeded expected executions: expected={}, actual={}",
            expected,
            currentSize
        );
      }
      return true;
    }

    return false;
  }

  private OrchestrationBundle buildBundle(MessageGroup group) {
    List<IngestResult> results = group
        .getMessages()
        .stream()
        .map(Message::getPayload)
        .filter(IngestResult.class::isInstance)
        .map(IngestResult.class::cast)
        .toList();

    Message<?> sample = group.getOne();
    MessageHeaders headers = sample != null
        ? sample.getHeaders()
        : new MessageHeaders(Map.of());

    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = headers.get(HDR_ETL_EVENT, String.class);
    LocalDate dateFrom = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
    LocalDate dateTo = headers.get(HDR_ETL_DATE_TO, LocalDate.class);

    MarketplaceEvent event = MarketplaceEvent.fromString(eventValue);
    if (event == null) {
      throw new AppException(ETL_REQUEST_INVALID, "event=" + eventValue);
    }

    boolean hasError = results.stream().anyMatch(IngestResult::isError);
    boolean hasWait = results.stream().anyMatch(IngestResult::isWait);

    Integer retryAfterSeconds = results.stream()
        .filter(IngestResult::isWait)
        .map(IngestResult::retryAfterSeconds)
        .filter(Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(null);

    List<String> failedSourceIds = results.stream()
        .filter(result -> result.isError() || result.isWait())
        .map(IngestResult::sourceId)
        .distinct()
        .toList();

    SyncStatus syncStatus;
    if (hasError) {
      syncStatus = SyncStatus.ERROR;
    } else if (hasWait) {
      syncStatus = SyncStatus.WAIT;
    } else {
      syncStatus = SyncStatus.SUCCESS;
    }

    String failedSourceIdsValue = String.join(",", failedSourceIds);

    List<String> errorMessages = results.stream()
        .filter(result -> result.isError() || result.isWait())
        .map(result -> {
          String errorMessage = result.errorMessage();
          if (StringUtils.isNotBlank(errorMessage)) {
            return errorMessage;
          }
          return "unknown error";
        })
        .distinct()
        .toList();

    String errorMessageValue = String.join("; ", errorMessages);

    return new OrchestrationBundle(
        requestId,
        accountId,
        event,
        dateFrom,
        dateTo,
        syncStatus,
        failedSourceIdsValue,
        errorMessageValue,
        results,
        retryAfterSeconds
    );
  }

  private String resolveWaitTtlMillis(OrchestrationBundle bundle) {
    Integer retryAfter = bundle.retryAfterSeconds();

    long waitSeconds = (retryAfter != null)
        ? Math.max(1L, retryAfter.longValue())
        : Math.max(1L, DEFAULT_WAIT_TTL_MILLIS / 1_000L);

    long ttlMillis = waitSeconds * 1_000L;

    return Long.toString(ttlMillis);
  }


  @Bean
  public IntegrationFlow etlOrchestratorWaitRetryFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .filter(
            OrchestrationBundle.class,
            bundle -> bundle.syncStatus() == SyncStatus.WAIT
        )
        .enrichHeaders(enricher -> enricher
            .headerFunction(
                AmqpHeaders.EXPIRATION,
                message -> resolveWaitTtlMillis((OrchestrationBundle) message.getPayload())
            )
        )
        .transform(
            OrchestrationBundle.class,
            bundle -> new OrchestrationCommand(
                bundle.requestId(),
                bundle.accountId(),
                bundle.event(),
                bundle.dateFrom(),
                bundle.dateTo()
            )
        )
        .handle(
            Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                .exchangeName(EXCHANGE_EXECUTION_DLX)
                .routingKey(ROUTING_KEY_EXECUTION_WAIT),
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  @Bean
  public IntegrationFlow etlOrchestratorMaterializationFlow(
      EtlMaterializationAdvice etlMaterializationAdvice
  ) {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .filter(
            OrchestrationBundle.class,
            bundle -> bundle.syncStatus() == SyncStatus.SUCCESS
        )
        .handle(
            OrchestrationBundle.class,
            (bundle, headers) -> {
              log.info(
                  "ETL orchestration completed successfully; starting materialization: requestId={}, event={}, from={}, to={}",
                  bundle.requestId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo()
              );

              materializationService.materialize(
                  bundle.accountId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo(),
                  bundle.requestId()
              );

              return bundle;
            },
            endpoint -> endpoint
                .requiresReply(false)
                .advice(etlMaterializationAdvice)
        )
        .nullChannel();
  }

  @Bean
  public IntegrationFlow etlOrchestratorErrorLogFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .filter(
            OrchestrationBundle.class,
            bundle -> bundle.syncStatus() == SyncStatus.ERROR
        )
        .handle(
            OrchestrationBundle.class,
            (bundle, headers) -> {
              log.warn(
                  "ETL orchestration completed with errors; materialization will NOT be started: requestId={}, event={}, from={}, to={}, failedSourceIds={}, errors={}",
                  bundle.requestId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo(),
                  bundle.failedSourceIds(),
                  bundle.errorMessage()
              );
              return null;
            },
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  @Bean
  public IntegrationFlow etlOrchestratorAuditFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .handle(
            OrchestrationBundle.class,
            (bundle, headers) -> {
              if (bundle.requestId() == null
                  || bundle.accountId() == null
                  || bundle.event() == null
                  || bundle.dateFrom() == null
                  || bundle.dateTo() == null) {
                log.warn(
                    "ETL audit skipped: missing required fields. requestId={}, accountId={}, event={}, from={}, to={}",
                    bundle.requestId(),
                    bundle.accountId(),
                    bundle.event(),
                    bundle.dateFrom(),
                    bundle.dateTo()
                );
                return null;
              }

              EtlSyncAuditDto dto = new EtlSyncAuditDto();
              dto.setRequestId(bundle.requestId());
              dto.setAccountId(bundle.accountId());
              dto.setEvent(bundle.event().name());
              dto.setDateFrom(bundle.dateFrom());
              dto.setDateTo(bundle.dateTo());
              dto.setStatus(bundle.syncStatus());
              dto.setFailedSources(bundle.failedSourceIds());
              dto.setErrorMessage(bundle.errorMessage());

              etlSyncAuditService.save(dto);

              log.info(
                  "ETL sync finished (audit): requestId={}, event={}, from={}, to={}, status={}, failedSourceIds={}, errors={}",
                  bundle.requestId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo(),
                  bundle.syncStatus(),
                  bundle.failedSourceIds(),
                  bundle.errorMessage()
              );

              return null;
            },
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  private Map<String, Object> buildAcceptedResponse(OrchestrationCommand command) {
    return Map.of(
        "status", "accepted",
        "requestId", command.requestId(),
        "event", command.event().name(),
        "from", command.from(),
        "to", command.to()
    );
  }

  private List<MarketplacePlan> buildMarketplacePlans(OrchestrationCommand command) {
    List<MarketplaceType> marketplaces =
        accountConnectionService.getActiveMarketplacesByAccountId(command.accountId());

    if (marketplaces.isEmpty()) {
      throw new AppException(
          MessageCodes.ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND,
          command.accountId()
      );
    }

    List<MarketplacePlan> plans = marketplaces.stream()
        .map(marketplace -> buildMarketplacePlan(command, marketplace))
        .filter(Objects::nonNull)
        .toList();

    if (plans.isEmpty()) {
      throw new AppException(
          MessageCodes.ETL_EVENT_SOURCES_MISSING,
          command.event().name()
      );
    }

    return plans;
  }

  private MarketplacePlan buildMarketplacePlan(
      OrchestrationCommand command,
      MarketplaceType marketplace
  ) {
    List<RegisteredSource> sources =
        etlSourceRegistry.findSources(command.event(), marketplace);
    if (sources.isEmpty()) {
      return null;
    }

    List<EtlSourceExecution> executions = sources.stream()
        .map(source -> new EtlSourceExecution(
            source.sourceId(),
            source.event(),
            marketplace,
            command.accountId(),
            command.from(),
            command.to(),
            source.order(),
            source.source(),
            source.rawTable()
        ))
        .toList();

    return new MarketplacePlan(marketplace, executions);
  }

  private int calculateExpectedSources(List<MarketplacePlan> plans) {
    return plans.stream()
        .mapToInt(plan -> plan.executions().size())
        .sum();
  }
}
