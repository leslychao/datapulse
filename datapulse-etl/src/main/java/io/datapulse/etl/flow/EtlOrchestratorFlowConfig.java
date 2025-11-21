package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ERRORS;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATE;
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
import io.datapulse.etl.route.EtlSourceRegistry;
import io.datapulse.etl.route.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.service.materialization.EtlMaterializationService;
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
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
public class EtlOrchestratorFlowConfig {

  private static final String HDR_ETL_EXPECTED_EXECUTIONS = "ETL_EXPECTED_EXECUTIONS";

  private final EtlSourceRegistry etlSourceRegistry;
  private final EtlMaterializationService etlMaterializationService;

  public EtlOrchestratorFlowConfig(
      EtlSourceRegistry etlSourceRegistry,
      EtlMaterializationService etlMaterializationService
  ) {
    this.etlSourceRegistry = etlSourceRegistry;
    this.etlMaterializationService = etlMaterializationService;
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

  /**
   * HTTP → fire-and-forget:
   * - сразу возвращаем 202 + body,
   * - оркестратор уходит в асинхрон через wireTap(CH_ETL_ORCHESTRATE).
   */
  @Bean
  public IntegrationFlow etlHttpInboundFlow() {
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
              "ETL orchestration requested: requestId={}, accountId={}, event={}, from={}, to={}",
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
        // Асинхронно шлём OrchestrationCommand в оркестратор
        .wireTap(CH_ETL_ORCHESTRATE)
        // А клиенту сразу отдаём body
        .handle((payload, headers) -> {
          String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
          String event = headers.get(HDR_ETL_EVENT, String.class);
          LocalDate from = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
          LocalDate to = headers.get(HDR_ETL_DATE_TO, LocalDate.class);

          Map<String, Object> body = new LinkedHashMap<>();
          body.put("status", "completed"); // как ты просил
          body.put("requestId", requestId);
          body.put("event", event);
          body.put("from", from);
          body.put("to", to);
          return body;
        })
        .get();
  }

  /**
   * Orchestrator: асинхронный consumer CH_ETL_ORCHESTRATE.
   * Строит планы, считает общее количество execution'ов, вызывает ingest
   * и ждёт все success-reply, после чего триггерит materialization.
   */
  @Bean
  public IntegrationFlow etlOrchestratorFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATE)
        .handle(OrchestrationCommand.class,
            (command, headers) -> buildMarketplacePlans(command)
        )
        // payload: List<MarketplacePlan> — считаем ожидаемое количество execution'ов
        .enrichHeaders(h -> h.headerFunction(HDR_ETL_EXPECTED_EXECUTIONS, message -> {
          Object payload = message.getPayload();
          if (!(payload instanceof List<?> payloadList)) {
            log.warn(
                "Unexpected payload type for expected executions calculation: {}",
                payload.getClass().getName()
            );
            return null;
          }

          int totalExecutions = 0;
          for (Object element : payloadList) {
            if (element instanceof MarketplacePlan plan) {
              totalExecutions += plan.executions().size();
            }
          }
          return totalExecutions;
        }))
        // Список MarketplacePlan
        .split()
        .enrichHeaders(h -> h
            .headerFunction(HDR_ETL_SOURCE_MP,
                m -> ((MarketplacePlan) m.getPayload()).marketplace())
        )
        .transform(MarketplacePlan.class, MarketplacePlan::executions)
        // Список EtlSourceExecution внутри каждого плана
        .split()
        .enrichHeaders(h -> h
            .headerFunction(HDR_ETL_SOURCE_ID,
                m -> ((EtlSourceExecution) m.getPayload()).sourceId())
        )
        // Для каждого EtlSourceExecution ждём reply от ingest-flow
        .gateway(
            CH_ETL_INGEST,
            e -> e
                .errorChannel(CH_ETL_ERRORS)
                .requestTimeout(0L)
                .replyTimeout(0L)
                .requiresReply(true)
        )
        // Собираем все успешные replies по requestId и количеству execution'ов
        .aggregate(a -> a
            .correlationStrategy(message ->
                message.getHeaders().get(HDR_ETL_REQUEST_ID)
            )
            .releaseStrategy(group -> {
              Message<?> anyMessage = group.getOne();
              if (anyMessage == null) {
                log.warn("Orchestrator aggregate completed with empty group");
                return true;
              }

              MessageHeaders headers = anyMessage.getHeaders();
              Integer expected = headers.get(
                  HDR_ETL_EXPECTED_EXECUTIONS,
                  Integer.class
              );

              if (expected == null) {
                log.warn(
                    "Orchestrator aggregate has no '{}' header, releasing immediately",
                    HDR_ETL_EXPECTED_EXECUTIONS
                );
                return true;
              }

              int currentSize = group.size();
              if (currentSize > expected) {
                log.warn(
                    "Orchestrator aggregate group size exceeded expected executions: expected={}, actual={}",
                    expected,
                    currentSize
                );
                return true;
              }

              return currentSize == expected;
            })
            .outputProcessor(group -> {
              Message<?> anyMessage = group.getOne();
              if (anyMessage == null) {
                log.warn("ETL orchestration aggregate completed with empty message group");
                return null;
              }

              MessageHeaders headers = anyMessage.getHeaders();

              String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
              Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
              String eventValue = headers.get(HDR_ETL_EVENT, String.class);
              LocalDate from = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
              LocalDate to = headers.get(HDR_ETL_DATE_TO, LocalDate.class);

              if (requestId == null
                  || accountId == null
                  || eventValue == null
                  || from == null
                  || to == null) {
                log.warn(
                    "Cannot trigger materialization: missing context headers; requestId={}, accountId={}, event={}, from={}, to={}",
                    requestId,
                    accountId,
                    eventValue,
                    from,
                    to
                );
                return null;
              }

              MarketplaceEvent event = MarketplaceEvent.fromString(eventValue);
              if (event == null) {
                log.warn(
                    "Cannot trigger materialization: unknown event '{}', requestId={}",
                    eventValue,
                    requestId
                );
                return null;
              }

              etlMaterializationService.materialize(
                  accountId,
                  event,
                  from,
                  to,
                  requestId
              );

              return null;
            })
            .requiresReply(false)
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
