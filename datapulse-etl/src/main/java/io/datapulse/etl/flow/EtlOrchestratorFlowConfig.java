package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ERRORS;
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
import io.datapulse.etl.flow.dto.EtlIngestResult;
import io.datapulse.etl.flow.dto.EtlSourceExecution;
import io.datapulse.etl.route.EtlSourceRegistry;
import io.datapulse.etl.route.EtlSourceRegistry.RegisteredSource;
import java.time.LocalDate;
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
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
public class EtlOrchestratorFlowConfig {

  private final EtlSourceRegistry etlSourceRegistry;

  public EtlOrchestratorFlowConfig(EtlSourceRegistry etlSourceRegistry) {
    this.etlSourceRegistry = etlSourceRegistry;
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

  @Bean
  public IntegrationFlow etlOrchestratorFlow(
      TaskExecutor etlOrchestrateExecutor,
      TaskExecutor etlIngestExecutor,
      EtlSnapshotIngestionFlowConfig ingestionFlowConfig
  ) {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestPayloadType(EtlRunRequest.class)
            .mappedRequestHeaders("*")
            .statusCodeFunction(m -> HttpStatus.ACCEPTED)
            .errorChannel(CH_ETL_ERRORS)
        )
        .enrichHeaders(headers -> headers
            .headerFunction(HDR_ETL_REQUEST_ID, m -> UUID.randomUUID().toString())
        )
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

          return new OrchestrationCommand(
              requestId,
              request.accountId(),
              event,
              request.from(),
              request.to()
          );
        })
        .enrichHeaders(headers -> headers
            .headerFunction(HDR_ETL_REQUEST_ID,
                m -> ((OrchestrationCommand) m.getPayload()).requestId())
            .headerFunction(HDR_ETL_ACCOUNT_ID,
                m -> ((OrchestrationCommand) m.getPayload()).accountId())
            .headerFunction(HDR_ETL_EVENT,
                m -> ((OrchestrationCommand) m.getPayload()).event().name())
            .headerFunction(HDR_ETL_DATE_FROM,
                m -> ((OrchestrationCommand) m.getPayload()).from())
            .headerFunction(HDR_ETL_DATE_TO,
                m -> ((OrchestrationCommand) m.getPayload()).to())
        )
        .handle(OrchestrationCommand.class, (command, headers) -> buildMarketplacePlans(command)
        )
        .split()
        .channel(c -> c.executor(etlOrchestrateExecutor))
        .enrichHeaders(h -> h
            .headerFunction(HDR_ETL_SOURCE_MP,
                m -> ((MarketplacePlan) m.getPayload()).marketplace())
        )
        .transform(MarketplacePlan.class, MarketplacePlan::executions)
        .split()
        .enrichHeaders(h -> h
            .headerFunction(HDR_ETL_SOURCE_ID,
                m -> ((EtlSourceExecution) m.getPayload()).sourceId())
        )
        .gateway(
            f -> f
                .channel(c -> c.executor(etlIngestExecutor))
                .handle(EtlSourceExecution.class, ingestionFlowConfig::handleIngest),
            e -> e
                .errorChannel(CH_ETL_ERRORS)
                .requestTimeout(0L)
                .replyTimeout(0L)
        )
        .aggregate()
        .handle((List<EtlIngestResult> results, MessageHeaders headers) -> {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("status", "completed");
          body.put("requestId", headers.get(HDR_ETL_REQUEST_ID, String.class));
          body.put("event", headers.get(HDR_ETL_EVENT, String.class));
          body.put("from", headers.get(HDR_ETL_DATE_FROM, LocalDate.class));
          body.put("to", headers.get(HDR_ETL_DATE_TO, LocalDate.class));
          body.put("sources", results);
          return body;
        })
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
