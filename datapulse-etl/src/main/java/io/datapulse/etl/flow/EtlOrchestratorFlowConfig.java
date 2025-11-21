package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_RUN_CORE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MP;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.flow.dto.EtlSourceExecution;
import io.datapulse.etl.flow.dto.IngestResult;
import io.datapulse.etl.route.EtlSourceRegistry;
import io.datapulse.etl.route.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.service.materialization.EtlMaterializationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
public class EtlOrchestratorFlowConfig {

  private static final String HDR_ETL_EXPECTED_SOURCES = "ETL_EXPECTED_SOURCES";

  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlMaterializationService materializationService;

  public EtlOrchestratorFlowConfig(
      EtlSourceRegistry etlSourceRegistry,
      EtlMaterializationService materializationService
  ) {
    this.etlSourceRegistry = etlSourceRegistry;
    this.materializationService = materializationService;
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
        .from(Http.inboundGateway("/api/etl/run")
            .requestPayloadType(EtlRunRequest.class)
            .statusCodeFunction(m -> HttpStatus.ACCEPTED)
        )
        .transform(EtlRunRequest.class, this::toOrchestrationCommand)
        .gateway(CH_ETL_RUN_CORE)
        .get();
  }

  @Bean
  public IntegrationFlow etlScheduledRunFlow() {
    return IntegrationFlow
        .fromSupplier(
            this::buildScheduledRunRequest,
            spec -> spec.poller(Pollers.cron("0 0 * * * *"))
        )
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
        .handle(OrchestrationCommand.class, (command, headers) -> {
          String requestId = command.requestId();
          Long accountId = command.accountId();
          MarketplaceEvent event = command.event();
          LocalDate from = command.from();
          LocalDate to = command.to();
          log.info(
              "ETL orchestration requested: requestId={}, accountId={}, event={}, from={}, to={}",
              requestId,
              accountId,
              event,
              from,
              to
          );
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("status", "accepted");
          body.put("requestId", requestId);
          body.put("event", event.name());
          body.put("from", from);
          body.put("to", to);
          return body;
        })
        .get();
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

  private EtlRunRequest buildScheduledRunRequest() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    return new EtlRunRequest(
        123L,
        "SALES",
        yesterday,
        yesterday
    );
  }

  @Bean
  public IntegrationFlow etlOrchestratorFlow(TaskExecutor etlOrchestrateExecutor) {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATE)
        .handle(OrchestrationCommand.class,
            (command, headers) -> buildMarketplacePlans(command)
        )
        .enrichHeaders(h -> h.headerFunction(
            HDR_ETL_EXPECTED_SOURCES,
            m -> {
              Object payload = m.getPayload();
              if (payload instanceof List<?> list) {
                return list.stream()
                    .filter(MarketplacePlan.class::isInstance)
                    .map(MarketplacePlan.class::cast)
                    .mapToInt(plan -> plan.executions().size())
                    .sum();
              }
              return 0;
            }
        ))
        .split()
        .enrichHeaders(h -> h.headerFunction(
            HDR_ETL_SOURCE_MP,
            message -> ((MarketplacePlan) message.getPayload()).marketplace()
        ))
        .transform(MarketplacePlan.class, MarketplacePlan::executions)
        .split()
        .enrichHeaders(h -> h.headerFunction(
            HDR_ETL_SOURCE_ID,
            message -> ((EtlSourceExecution) message.getPayload()).sourceId()
        ))
        .channel(c -> c.executor(etlOrchestrateExecutor))
        .gateway(
            CH_ETL_INGEST,
            spec -> spec
                .requestTimeout(0L)
                .replyTimeout(0L)
                .requiresReply(true)
        )
        .aggregate(aggregator -> aggregator
            .correlationStrategy(
                message -> message.getHeaders().get(HDR_ETL_REQUEST_ID)
            )
            .releaseStrategy(group -> {
              Message<?> sampleMessage = group.getOne();
              if (sampleMessage == null) {
                log.warn("Orchestrator aggregate completed with empty group");
                return true;
              }

              MessageHeaders headers = sampleMessage.getHeaders();
              Integer expected = headers.get(HDR_ETL_EXPECTED_SOURCES, Integer.class);
              if (expected == null) {
                log.warn(
                    "Orchestrator aggregate has no '{}' header, releasing immediately",
                    HDR_ETL_EXPECTED_SOURCES
                );
                return true;
              }

              int currentSize = group.size();
              if (currentSize >= expected) {
                if (currentSize > expected) {
                  log.warn(
                      "Orchestrator aggregate group size exceeded expected sources: expected={}, actual={}",
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
              List<IngestResult> results = new ArrayList<>();
              for (Message<?> msg : group.getMessages()) {
                Object p = msg.getPayload();
                if (p instanceof IngestResult r) {
                  results.add(r);
                }
              }
              return results;
            })
        )
        .handle(
            List.class,
            (payload, headers) -> {
              List<IngestResult> results = new ArrayList<>();
              for (Object element : payload) {
                if (element instanceof IngestResult result) {
                  results.add(result);
                }
              }

              String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
              Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
              String eventValue = headers.get(HDR_ETL_EVENT, String.class);
              LocalDate from = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
              LocalDate to = headers.get(HDR_ETL_DATE_TO, LocalDate.class);

              MarketplaceEvent event = MarketplaceEvent.fromString(eventValue);

              List<String> failedSources = results.stream()
                  .filter(result -> !result.success())
                  .map(IngestResult::sourceId)
                  .toList();

              if (!failedSources.isEmpty()) {
                log.warn(
                    "ETL orchestration completed with failures; materialization will NOT be started: "
                        + "requestId={}, event={}, from={}, to={}, failedSources={}",
                    requestId,
                    event,
                    from,
                    to,
                    failedSources
                );
                return null;
              }

              log.info(
                  "ETL orchestration completed successfully; starting materialization: "
                      + "requestId={}, event={}, from={}, to={}",
                  requestId,
                  event,
                  from,
                  to
              );

              materializationService.materialize(
                  accountId,
                  event,
                  from,
                  to,
                  requestId
              );
              return null;
            }
        )
        .get();
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
        .map(marketplace -> {
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
        })
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
}
