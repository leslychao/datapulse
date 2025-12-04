package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_EXECUTION_DISPATCH;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_RUN_CORE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EXPECTED_SOURCE_IDS;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_RAW_TABLE;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_SOURCE_MARKETPLACE;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.core.service.EtlSyncAuditService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.OrchestrationBundle;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.flow.advice.EtlOrchestratorPlansAdvice;
import io.datapulse.etl.flow.core.OrchestrationAggregationHelper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlOrchestratorFlowConfig {

  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlSyncAuditService etlSyncAuditService;
  private final EtlOrchestrationCommandFactory orchestrationCommandFactory;
  private final OrchestrationAggregationHelper aggregationHelper;

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
        .channel(CH_ETL_RUN_CORE)
        .transform(OrchestrationCommand.class, this::buildAcceptedResponse)
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
            m -> "ETL run command dispatched to orchestrator: " + m.getPayload()
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
                        HDR_ETL_EXPECTED_SOURCE_IDS,
                        message -> {
                          List<MarketplacePlan> plans = castMarketplacePlans(message.getPayload());
                          List<String> expectedSourceIds = calculateExpectedSourceIds(plans);
                          return expectedSourceIds.toArray(String[]::new);
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
                    .channel(CH_ETL_EXECUTION_DISPATCH)
                )
        )
        .get();
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

              EtlSyncAuditDto dto = aggregationHelper.buildAuditDto(bundle);

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

  private List<MarketplacePlan> castMarketplacePlans(Object payload) {
    if (!(payload instanceof List<?> rawPlans)) {
      log.warn(
          "Unexpected payload type for orchestrator plans calculation: {}",
          payload != null ? payload.getClass().getName() : "null"
      );
      return List.of();
    }

    return rawPlans.stream()
        .filter(MarketplacePlan.class::isInstance)
        .map(MarketplacePlan.class::cast)
        .toList();
  }

  private List<String> calculateExpectedSourceIds(List<MarketplacePlan> plans) {
    return plans.stream()
        .flatMap(plan -> plan.executions().stream())
        .map(EtlSourceExecution::sourceId)
        .toList();
  }
}
