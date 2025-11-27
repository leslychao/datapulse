package io.datapulse.etl.flow;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_RUN_CORE;
import static io.datapulse.etl.flow.EtlFlowConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.flow.EtlFlowConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MP;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_TOTAL_EXECUTIONS;
import static io.datapulse.etl.flow.EtlFlowConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.flow.EtlFlowConstants.ROUTING_KEY_EXECUTION;
import static io.datapulse.etl.flow.EtlFlowConstants.ROUTING_KEY_EXECUTION_WAIT;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.core.service.AccountService;
import io.datapulse.core.service.EtlSyncAuditService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.IngestResult;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.flow.advice.EtlMaterializationAdvice;
import io.datapulse.etl.service.EtlMaterializationService;
import io.micrometer.common.util.StringUtils;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
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
  private final AccountService accountService;
  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlMaterializationService materializationService;
  private final EtlSyncAuditService etlSyncAuditService;

  public record EtlRunRequest(
      Long accountId,
      String event,
      LocalDate from,
      LocalDate to,

      Integer burst
  ) {

  }

  public record OrchestrationCommand(
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to
  ) {

  }

  private record MarketplacePlan(
      MarketplaceType marketplace,
      List<EtlSourceExecution> executions
  ) {

  }

  private record RequiredField(
      String name,
      Object value
  ) {

  }

  public record OrchestrationBundle(
      String requestId,
      Long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo,
      SyncStatus syncStatus,
      String failedSourceIds,
      String errorMessage,
      List<IngestResult> results
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
        .enrichHeaders(enricher -> enricher.headerFunction(
            "burst",
            message -> {
              EtlRunRequest request = (EtlRunRequest) message.getPayload();
              Integer burst = request.burst();
              return burst != null && burst > 0 ? burst : 1;
            }
        ))
        .transform(EtlRunRequest.class, this::toOrchestrationCommand)
        .wireTap(tap -> tap.handle(
            OrchestrationCommand.class,
            (command, headers) -> {
              Integer burst = headers.get("burst", Integer.class);
              int times = burst != null && burst > 0 ? burst : 1;

              for (int i = 0; i < times; i++) {
                etlExecutionRabbitTemplate.convertAndSend(
                    EXCHANGE_EXECUTION,
                    ROUTING_KEY_EXECUTION,
                    command
                );
              }
              return null;
            }
        ))
        .transform(OrchestrationCommand.class, this::buildAcceptedResponse)
        .get();
  }

  @Bean
  public IntegrationFlow etlScheduledRunFlow() {
    return IntegrationFlow
        .fromSupplier(
            this::buildScheduledRunRequests,
            spec -> spec.poller(Pollers.cron("0 0 * * * *"))
        )
        .split()
        .transform(EtlRunRequest.class, this::toOrchestrationCommand)
        .handle(
            Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                .exchangeName(EXCHANGE_EXECUTION)
                .routingKey(ROUTING_KEY_EXECUTION)
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
  public IntegrationFlow etlOrchestratorFlow(TaskExecutor etlOrchestrateExecutor) {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATE)
        .headerFilter(MessageHeaders.REPLY_CHANNEL, MessageHeaders.ERROR_CHANNEL)
        .transform(OrchestrationCommand.class, this::buildMarketplacePlans)
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_ETL_TOTAL_EXECUTIONS,
            message -> {
              Object payload = message.getPayload();
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
        .channel(channelSpec -> channelSpec.executor(etlOrchestrateExecutor))
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_ETL_SOURCE_MP,
            message -> {
              MarketplacePlan plan = (MarketplacePlan) message.getPayload();
              return plan.marketplace();
            }
        ))
        .transform(MarketplacePlan.class, MarketplacePlan::executions)
        .split()
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_ETL_SOURCE_ID,
            message -> {
              EtlSourceExecution execution = (EtlSourceExecution) message.getPayload();
              return execution.sourceId();
            }
        ))
        .gateway(
            CH_ETL_INGEST,
            gatewaySpec -> gatewaySpec
                .requestTimeout(0L)
                .replyTimeout(0L)
                .requiresReply(true)
        )
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message ->
                message.getHeaders().get(HDR_ETL_REQUEST_ID, String.class)
            )
            .releaseStrategy(this::isFullGroup)
            .expireGroupsUponCompletion(true)
            .outputProcessor(this::buildBundle)
        )
        .channel(CH_ETL_ORCHESTRATION_RESULT)
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

    List<String> failedSourceIds = results.stream()
        .filter(IngestResult::isError)
        .map(IngestResult::sourceId)
        .distinct()
        .toList();

    SyncStatus syncStatus = failedSourceIds.isEmpty()
        ? SyncStatus.SUCCESS
        : SyncStatus.ERROR;

    String failedSourceIdsValue = String.join(",", failedSourceIds);

    List<String> errorMessages = results.stream()
        .filter(IngestResult::isError)
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
        results
    );
  }

  @Bean
  public IntegrationFlow etlOrchestratorWaitRetryFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .filter(
            OrchestrationBundle.class,
            bundle -> {
              boolean hasWait = bundle.results().stream().anyMatch(IngestResult::isWait);
              boolean hasError = bundle.results().stream().anyMatch(IngestResult::isError);
              return hasWait && !hasError;
            }
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
            bundle -> {
              boolean hasWait = bundle.results().stream().anyMatch(IngestResult::isWait);
              if (hasWait) {
                return false;
              }
              return bundle.syncStatus() == SyncStatus.SUCCESS;
            }
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
            bundle -> bundle.syncStatus() != SyncStatus.SUCCESS
        )
        .handle(
            OrchestrationBundle.class,
            (bundle, headers) -> {
              log.warn(
                  "ETL orchestration completed with errors; materialization will NOT be started: requestId={}, event={}, from={}, to={}, failedSourceIds={}",
                  bundle.requestId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo(),
                  bundle.failedSourceIds()
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

              if (bundle.syncStatus() == SyncStatus.SUCCESS) {
                log.info(
                    "ETL sync finished successfully (audit): requestId={}, event={}, from={}, to={}",
                    bundle.requestId(),
                    bundle.event(),
                    bundle.dateFrom(),
                    bundle.dateTo()
                );
              } else {
                log.warn(
                    "ETL sync finished with errors (audit): requestId={}, event={}, from={}, to={}, failedSourceIds={}, errors={}",
                    bundle.requestId(),
                    bundle.event(),
                    bundle.dateFrom(),
                    bundle.dateTo(),
                    bundle.failedSourceIds(),
                    bundle.errorMessage()
                );
              }

              return null;
            },
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  private Map<String, Object> buildAcceptedResponse(OrchestrationCommand command) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "accepted");
    body.put("requestId", command.requestId());
    body.put("event", command.event().name());
    body.put("from", command.from());
    body.put("to", command.to());
    return body;
  }

  private OrchestrationCommand toOrchestrationCommand(EtlRunRequest request) {
    validateRunRequest(request);

    MarketplaceEvent event = MarketplaceEvent.fromString(request.event());
    if (event == null) {
      throw new AppException(
          MessageCodes.ETL_REQUEST_INVALID,
          "event=" + request.event()
      );
    }

    String requestId = UUID.randomUUID().toString();

    return new OrchestrationCommand(
        requestId,
        request.accountId(),
        event,
        request.from(),
        request.to()
    );
  }

  private List<EtlRunRequest> buildScheduledRunRequests() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    LocalDate today = LocalDate.now();

    return accountService.streamActive()
        .map(AccountDto::getId)
        .map(accountId -> new EtlRunRequest(
            accountId,
            MarketplaceEvent.SALES_FACT.name(),
            yesterday,
            today,
            1
        ))
        .toList();
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
            source.source()
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
