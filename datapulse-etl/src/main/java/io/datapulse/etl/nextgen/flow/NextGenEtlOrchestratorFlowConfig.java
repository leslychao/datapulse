package io.datapulse.etl.nextgen.flow;

import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.EXCHANGE_EXECUTION;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.QUEUE_EXECUTION;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.ROUTING_EXECUTION;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.ROUTING_EXECUTION_WAIT;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_AUDIT;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_EXECUTION;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_INGEST;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_MATERIALIZE;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_MATERIALIZE_GATE;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_NGES_REQUEST;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_NORMALIZE;
import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_ORCHESTRATE;

import io.datapulse.etl.nextgen.dto.EventCommand;
import io.datapulse.etl.nextgen.dto.EventStatus;
import io.datapulse.etl.nextgen.dto.ExecutionCommand;
import io.datapulse.etl.nextgen.dto.ExecutionDispatch;
import io.datapulse.etl.nextgen.dto.ExecutionResult;
import io.datapulse.etl.nextgen.dto.ExecutionStatus;
import io.datapulse.etl.nextgen.dto.MaterializationRequest;
import io.datapulse.etl.nextgen.dto.NormalizationPayload;
import io.datapulse.etl.nextgen.dto.RawIngestPayload;
import io.datapulse.etl.nextgen.dto.RetrySignal;
import io.datapulse.etl.nextgen.service.AuditService;
import io.datapulse.etl.nextgen.service.EventLifecycleService;
import io.datapulse.etl.nextgen.service.ExecutionLifecycleService;
import io.datapulse.etl.nextgen.service.ExecutionPlanFactory;
import io.datapulse.etl.nextgen.service.IngestService;
import io.datapulse.etl.nextgen.service.MaterializationService;
import io.datapulse.etl.nextgen.service.NormalizationService;
import io.datapulse.etl.nextgen.constants.NextGenEtlHeaders;
import io.datapulse.marketplaces.resilience.TooManyRequestsBackoffRequiredException;
import java.util.Objects;
import java.util.UUID;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.dsl.amqp.Amqp;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class NextGenEtlOrchestratorFlowConfig {

  private final ExecutionPlanFactory executionPlanFactory;
  private final ExecutionLifecycleService executionLifecycleService;
  private final EventLifecycleService eventLifecycleService;
  private final IngestService ingestService;
  private final NormalizationService normalizationService;
  private final MaterializationService materializationService;
  private final AuditService auditService;
  private final RabbitTemplate rabbitTemplate;
  private final ConnectionFactory connectionFactory;
  private final TaskExecutor marketplaceParallelExecutor;

  public NextGenEtlOrchestratorFlowConfig(
      ExecutionPlanFactory executionPlanFactory,
      ExecutionLifecycleService executionLifecycleService,
      EventLifecycleService eventLifecycleService,
      IngestService ingestService,
      NormalizationService normalizationService,
      MaterializationService materializationService,
      AuditService auditService,
      RabbitTemplate rabbitTemplate,
      ConnectionFactory connectionFactory
  ) {
    this.executionPlanFactory = executionPlanFactory;
    this.executionLifecycleService = executionLifecycleService;
    this.eventLifecycleService = eventLifecycleService;
    this.ingestService = ingestService;
    this.normalizationService = normalizationService;
    this.materializationService = materializationService;
    this.auditService = auditService;
    this.rabbitTemplate = rabbitTemplate;
    this.connectionFactory = connectionFactory;
    this.marketplaceParallelExecutor = buildMarketplaceExecutor();
  }

  @Bean(name = CH_ORCHESTRATE)
  public MessageChannel orchestrateChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_NGES_REQUEST)
  public MessageChannel ngesChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_EXECUTION)
  public MessageChannel executionChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_INGEST)
  public MessageChannel ingestChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_NORMALIZE)
  public MessageChannel normalizeChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_MATERIALIZE_GATE)
  public MessageChannel materializationGateChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_MATERIALIZE)
  public MessageChannel materializationChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_AUDIT)
  public MessageChannel auditChannel() {
    return new DirectChannel();
  }

  private TaskExecutor buildMarketplaceExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(16);
    executor.setThreadNamePrefix("ng-marketplace-");
    executor.initialize();
    return executor;
  }

  @Bean
  public IntegrationFlow nextGenOrchestratorFlow() {
    return IntegrationFlows
        .from(CH_ORCHESTRATE)
        .handle(EventCommand.class, (command, headers) -> {
          eventLifecycleService.start(command.eventId());
          return executionPlanFactory.plan(command);
        })
        .split()
        .channel(channel -> channel.executor(this.marketplaceParallelExecutor))
        .resequence(spec -> spec
            .correlationExpression("payload.marketplace()")
            .releasePartialSequences(true)
            .comparator((messageOne, messageTwo) -> {
              ExecutionCommand left = (ExecutionCommand) messageOne.getPayload();
              ExecutionCommand right = (ExecutionCommand) messageTwo.getPayload();
              return Integer.compare(left.orderIndex(), right.orderIndex());
            }))
        .enrichHeaders(headers -> headers
            .headerFunction(NextGenEtlHeaders.HDR_MARKETPLACE, message -> ((ExecutionCommand) message.getPayload()).marketplace()))
        .handle(Amqp.outboundAdapter(rabbitTemplate)
            .exchangeName(EXCHANGE_EXECUTION)
            .routingKey(ROUTING_EXECUTION))
        .get();
  }

  @Bean
  public IntegrationFlow nextGenExecutionInboundFlow() {
    return IntegrationFlows
        .from(Amqp.inboundAdapter(connectionFactory, QUEUE_EXECUTION))
        .channel(CH_NGES_REQUEST)
        .get();
  }

  @Bean
  public IntegrationFlow nextGenNgesFlow() {
    return IntegrationFlows
        .from(CH_NGES_REQUEST)
        .handle(ExecutionCommand.class, (command, headers) -> handleNges(command, headers),
            endpoint -> endpoint.advice(waitAdvice()))
        .route(Object.class, payload -> payload.getClass(), mapping -> mapping
            .subFlowMapping(ExecutionDispatch.class, sub -> sub.channel(CH_EXECUTION))
            .subFlowMapping(RetrySignal.class, sub -> sub
                .enrichHeaders(headers -> headers
                    .headerFunction(AmqpHeaders.EXPIRATION, message -> {
                      RetrySignal signal = (RetrySignal) message.getPayload();
                      return String.valueOf(signal.retryAfterSeconds() * 1000L);
                    }))
                .transform(RetrySignal.class, signal -> new ExecutionDispatch(
                    signal.executionId(),
                    signal.eventId(),
                    signal.sourceName(),
                    signal.marketplace(),
                    signal.retryCount()))
                .handle(Amqp.outboundAdapter(rabbitTemplate)
                    .exchangeName(EXCHANGE_EXECUTION)
                    .routingKey(ROUTING_EXECUTION_WAIT))
            ))
        .get();
  }

  private ExecutionDispatch handleNges(ExecutionCommand command, MessageHeaders headers) {
    executionLifecycleService.register(command.executionId());
    executionLifecycleService.markInProgress(command.executionId());
    return new ExecutionDispatch(command.executionId(), command.eventId(), command.sourceName(), command.marketplace(), command.orderIndex(), command.retryCount());
  }

  @Bean
  public IntegrationFlow nextGenExecutionFlow() {
    return IntegrationFlows
        .from(CH_EXECUTION)
        .routeToRecipients(router -> router
            .recipient(CH_INGEST)
            .recipient(CH_AUDIT))
        .get();
  }

  @Bean
  public IntegrationFlow nextGenIngestFlow() {
    return IntegrationFlows
        .from(CH_INGEST)
        .transform(ExecutionDispatch.class, dispatch -> {
          ExecutionCommand command = new ExecutionCommand(
              dispatch.executionId(),
              dispatch.eventId(),
              dispatch.sourceName(),
              dispatch.marketplace(),
              dispatch.orderIndex(),
              dispatch.retryCount()
          );
          String snapshot = "[]";
          RawIngestPayload payload = ingestService.ingest(command, snapshot);
          return payload;
        })
        .transform(RawIngestPayload.class, payload -> new NormalizationPayload(
            payload.executionId(),
            payload.eventId(),
            payload.sourceName(),
            payload.rawRowsCount()
        ))
        .channel(CH_NORMALIZE)
        .get();
  }

  @Bean
  public IntegrationFlow nextGenNormalizationFlow() {
    return IntegrationFlows
        .from(CH_NORMALIZE)
        .handle(NormalizationPayload.class, (payload, headers) -> normalizationService.normalize(payload))
        .channel(CH_MATERIALIZE_GATE)
        .get();
  }

  @Bean
  public IntegrationFlow nextGenMaterializationGateFlow() {
    return IntegrationFlows
        .from(CH_MATERIALIZE_GATE)
        .handle(ExecutionResult.class, (result, headers) -> {
          auditService.recordExecution(
              result.executionId(),
              result.eventId(),
              headers.get("marketplace", String.class),
              headers.get("source", String.class),
              result.status(),
              result.rawRowsCount(),
              0,
              result.errorCode(),
              result.errorMessage()
          );
          eventLifecycleService.registerExecution(result.eventId(), result.status());
          if (eventLifecycleService.isMaterializationAllowed(result.eventId())) {
            return new MaterializationRequest(result.eventId(), eventLifecycleService.executionStatuses(result.eventId()));
          }
          return null;
        }, endpoint -> endpoint.requiresReply(false))
        .filter(Objects::nonNull)
        .channel(CH_MATERIALIZE)
        .get();
  }

  @Bean
  public IntegrationFlow nextGenMaterializationFlow() {
    return IntegrationFlows
        .from(CH_MATERIALIZE)
        .handle(MaterializationRequest.class, (request, headers) -> {
          EventStatus status = materializationService.materialize(request);
          EventStatus finalStatus = eventLifecycleService.finalizeStatus(request.eventId());
          auditService.recordEvent(
              request.eventId(),
              null,
              null,
              finalStatus,
              eventLifecycleService.startedAt(request.eventId()),
              eventLifecycleService.finishedAt(request.eventId()),
              request.executionStatuses().stream().filter(s -> s == ExecutionStatus.SUCCESS).count(),
              (int) request.executionStatuses().stream().filter(s -> s == ExecutionStatus.SUCCESS).count(),
              (int) request.executionStatuses().stream().filter(s -> s == ExecutionStatus.FAILED_FINAL).count()
          );
          return status;
        })
        .get();
  }

  @Bean
  public Advice waitAdvice() {
    return new AbstractRequestHandlerAdvice() {
      @Override
      protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
        try {
          return callback.execute();
        } catch (Exception ex) {
          if (ex instanceof TooManyRequestsBackoffRequiredException tooManyRequests) {
            int retryAfter = tooManyRequests.getRetryAfterSeconds();
            ExecutionDispatch dispatch = (ExecutionDispatch) message.getPayload();
            executionLifecycleService.markWaitingRetry(dispatch.executionId(), dispatch.retryCount() + 1);
            return new RetrySignal(
                dispatch.executionId(),
                dispatch.eventId(),
                dispatch.sourceName(),
                dispatch.marketplace(),
                retryAfter,
                dispatch.retryCount() + 1
            );
          }
          throw ex;
        }
      }
    };
  }
}
