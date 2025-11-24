package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_RUN_CORE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ERROR_MESSAGE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_FAILED_SOURCE_IDS;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MP;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SYNC_STATUS;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_TOTAL_EXECUTIONS;

import io.datapulse.core.service.AccountService;
import io.datapulse.core.service.EtlSyncAuditService;
import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.flow.dto.EtlSourceExecution;
import io.datapulse.etl.flow.dto.IngestResult;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.service.materialization.EtlMaterializationService;
import io.micrometer.common.util.StringUtils;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlOrchestratorFlowConfig {

  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlMaterializationService materializationService;
  private final AccountService accountService;
  private final EtlSyncAuditService etlSyncAuditService;

  public record EtlRunRequest(
      Long accountId,
      String event,
      LocalDate from,
      LocalDate to
  ) {

  }

  private record OrchestrationCommand(
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

  private record RequiredField(String name, Object value) {

  }

  private record EtlAuditContext(
      String requestId,
      Long accountId,
      String eventValue,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {

    boolean hasAllRequiredFields() {
      return requestId != null
          && accountId != null
          && eventValue != null
          && dateFrom != null
          && dateTo != null;
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
    executor.initialize();
    return executor;
  }

  @Bean(name = CH_ETL_ORCHESTRATE)
  public MessageChannel etlOrchestrateChannel(TaskExecutor etlOrchestrateExecutor) {
    return new ExecutorChannel(etlOrchestrateExecutor);
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
        .gateway(CH_ETL_RUN_CORE)
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
        .gateway(CH_ETL_RUN_CORE)
        .get();
  }

  @Bean
  public IntegrationFlow etlRunCoreFlow() {
    return IntegrationFlow
        .from(CH_ETL_RUN_CORE)
        .enrichHeaders(headers -> headers
            .headerFunction(
                HDR_ETL_REQUEST_ID,
                message -> ((OrchestrationCommand) message.getPayload()).requestId()
            )
            .headerFunction(
                HDR_ETL_ACCOUNT_ID,
                message -> ((OrchestrationCommand) message.getPayload()).accountId()
            )
            .headerFunction(
                HDR_ETL_EVENT,
                message -> ((OrchestrationCommand) message.getPayload()).event().name()
            )
            .headerFunction(
                HDR_ETL_DATE_FROM,
                message -> ((OrchestrationCommand) message.getPayload()).from()
            )
            .headerFunction(
                HDR_ETL_DATE_TO,
                message -> ((OrchestrationCommand) message.getPayload()).to()
            )
        )
        .wireTap(CH_ETL_ORCHESTRATE)
        .handle(
            OrchestrationCommand.class,
            (command, headersMap) -> buildAcceptedResponse(command)
        )
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
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_ETL_SOURCE_MP,
            message -> ((MarketplacePlan) message.getPayload()).marketplace()
        ))
        .transform(MarketplacePlan.class, MarketplacePlan::executions)
        .split()
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_ETL_SOURCE_ID,
            message -> ((EtlSourceExecution) message.getPayload()).sourceId()
        ))
        .channel(channelSpec -> channelSpec.executor(etlOrchestrateExecutor))
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
            .releaseStrategy(group -> {
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
            })
            .expireGroupsUponCompletion(true)
            .outputProcessor(group -> {
              List<IngestResult> results = group
                  .getMessages()
                  .stream()
                  .map(Message::getPayload)
                  .filter(IngestResult.class::isInstance)
                  .map(IngestResult.class::cast)
                  .toList();

              List<String> failedSourceIds = results.stream()
                  .filter(result -> !result.success())
                  .map(IngestResult::sourceId)
                  .toList();

              SyncStatus syncStatus = failedSourceIds.isEmpty()
                  ? SyncStatus.SUCCESS
                  : SyncStatus.ERROR;

              String failedSourceIdsValue = String.join(",", failedSourceIds);

              List<String> errorMessages = results.stream()
                  .filter(result -> !result.success())
                  .map(result -> {
                    String errorMessage = result.errorMessage();
                    if (StringUtils.isNotBlank(errorMessage)) {
                      return errorMessage;
                    }
                    return "unknown error";
                  })
                  .toList();

              String errorMessageValue = String.join("; ", errorMessages);

              Message<?> sample = group.getOne();
              MessageHeaders originalHeaders = sample != null ? sample.getHeaders() : null;

              return MessageBuilder
                  .withPayload(results)
                  .copyHeadersIfAbsent(originalHeaders != null ? originalHeaders : Map.of())
                  .setHeader(HDR_ETL_SYNC_STATUS, syncStatus)
                  .setHeader(HDR_ETL_FAILED_SOURCE_IDS, failedSourceIdsValue)
                  .setHeader(HDR_ETL_ERROR_MESSAGE, errorMessageValue)
                  .build();
            })
        )
        .handle(
            Object.class,
            this::handleOrchestratorResults,
            endpoint -> endpoint.requiresReply(true)
        )
        .handle(
            Object.class,
            this::handleAudit,
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  private Object handleOrchestratorResults(Object payload, MessageHeaders headers) {
    EtlAuditContext context = extractAuditContext(headers);

    MarketplaceEvent event = MarketplaceEvent.fromString(context.eventValue());
    if (event == null) {
      throw new AppException(
          MessageCodes.ETL_REQUEST_INVALID,
          "event=" + context.eventValue()
      );
    }

    SyncStatus syncStatus = headers.get(HDR_ETL_SYNC_STATUS, SyncStatus.class);
    String failedSourceIds = headers.get(HDR_ETL_FAILED_SOURCE_IDS, String.class);
    String failedSourceIdsValue = failedSourceIds != null ? failedSourceIds : "";

    if (syncStatus != SyncStatus.SUCCESS) {
      log.warn(
          "ETL orchestration completed with errors; materialization will NOT be started: "
              + "requestId={}, event={}, from={}, to={}, failedSourceIds={}",
          context.requestId(),
          event,
          context.dateFrom(),
          context.dateTo(),
          failedSourceIdsValue
      );
      return payload;
    }

    log.info(
        "ETL orchestration completed successfully; starting materialization: "
            + "requestId={}, event={}, from={}, to={}",
        context.requestId(),
        event,
        context.dateFrom(),
        context.dateTo()
    );

    materializationService.materialize(
        context.accountId(),
        event,
        context.dateFrom(),
        context.dateTo(),
        context.requestId()
    );

    return payload;
  }

  private Object handleAudit(Object payload, MessageHeaders headers) {
    EtlAuditContext context = extractAuditContext(headers);

    if (!context.hasAllRequiredFields()) {
      log.warn(
          "ETL аудит пропущен: не хватает обязательных заголовков. requestId={}, accountId={}, event={}, from={}, to={}",
          context.requestId(),
          context.accountId(),
          context.eventValue(),
          context.dateFrom(),
          context.dateTo()
      );
      return null;
    }

    SyncStatus syncStatus = headers.get(HDR_ETL_SYNC_STATUS, SyncStatus.class);
    String failedSourceIds = headers.get(HDR_ETL_FAILED_SOURCE_IDS, String.class);
    String errorMessage = headers.get(HDR_ETL_ERROR_MESSAGE, String.class);

    String failedSourceIdsValue = failedSourceIds != null ? failedSourceIds : "";
    String errorMessageValue = errorMessage != null ? errorMessage : "";

    EtlSyncAuditDto dto = new EtlSyncAuditDto();
    dto.setRequestId(context.requestId());
    dto.setAccountId(context.accountId());
    dto.setEvent(context.eventValue());
    dto.setDateFrom(context.dateFrom());
    dto.setDateTo(context.dateTo());
    dto.setStatus(syncStatus != null ? syncStatus : SyncStatus.ERROR);
    dto.setFailedSources(failedSourceIdsValue);
    dto.setErrorMessage(errorMessageValue);

    etlSyncAuditService.save(dto);

    if (syncStatus == SyncStatus.SUCCESS) {
      log.info(
          "ETL sync finished successfully (audit): requestId={}, event={}, from={}, to={}",
          context.requestId(),
          context.eventValue(),
          context.dateFrom(),
          context.dateTo()
      );
    } else {
      log.warn(
          "ETL sync finished with errors (audit): requestId={}, event={}, from={}, to={}, failedSourceIds={}, errors={}",
          context.requestId(),
          context.eventValue(),
          context.dateFrom(),
          context.dateTo(),
          failedSourceIdsValue,
          errorMessageValue
      );
    }

    return null;
  }

  private EtlAuditContext extractAuditContext(MessageHeaders headers) {
    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = headers.get(HDR_ETL_EVENT, String.class);
    LocalDate from = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
    LocalDate to = headers.get(HDR_ETL_DATE_TO, LocalDate.class);

    return new EtlAuditContext(
        requestId,
        accountId,
        eventValue,
        from,
        to
    );
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
            today
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
    List<MarketplacePlan> plans = Stream
        .of(MarketplaceType.values())
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
