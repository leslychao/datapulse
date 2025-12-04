package io.datapulse.etl.integration.config;

import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;
import static io.datapulse.etl.integration.config.RabbitTopology.AUDIT_QUEUE;
import static io.datapulse.etl.integration.config.RabbitTopology.EVENT_ORCHESTRATOR_QUEUE;
import static io.datapulse.etl.integration.config.RabbitTopology.EXECUTION_WORKER_QUEUE;
import static io.datapulse.etl.integration.config.RabbitTopology.MATERIALIZATION_QUEUE;
import static io.datapulse.etl.integration.config.RabbitTopology.RETRY_WAIT_QUEUE;

import io.datapulse.etl.application.service.ProcessExecutionService;
import io.datapulse.etl.application.service.ProcessRetryTimeoutService;
import io.datapulse.etl.application.service.RecordAuditService;
import io.datapulse.etl.application.service.RunMaterializationService;
import io.datapulse.etl.application.service.StartEventSyncService;
import io.datapulse.etl.domain.event.DomainEvent;
import io.datapulse.etl.integration.messaging.OrchestrationCommand;
import io.datapulse.etl.integration.messaging.EventStartCommand;
import io.datapulse.etl.integration.messaging.ExecutionCommand;
import io.datapulse.etl.integration.messaging.MaterializationCommand;
import io.datapulse.etl.integration.messaging.RetryCommand;
import io.datapulse.etl.integration.web.EtlRunRequest;
import io.datapulse.etl.integration.web.OrchestrationAcceptedResponse;
import io.datapulse.etl.integration.web.OrchestrationCommandFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;

@Configuration
@RequiredArgsConstructor
public class EtlIntegrationFlows {

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final OrchestrationCommandFactory orchestrationCommandFactory;

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
  public IntegrationFlow executionWorkerFlow(ProcessExecutionService processExecutionService) {
    return IntegrationFlow.from(EXECUTION_WORKER_QUEUE)
        .handle(ExecutionCommand.class, (command, headers) -> {
          processExecutionService.process(command.executionId(), command.plan(), command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow retryWaitFlow(ProcessRetryTimeoutService processRetryTimeoutService) {
    return IntegrationFlow.from(RETRY_WAIT_QUEUE)
        .handle(RetryCommand.class, (command, headers) -> {
          processRetryTimeoutService.processRetry(command.executionId(), command.request(),
              command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow eventOrchestratorFlow(StartEventSyncService startEventSyncService) {
    return IntegrationFlow.from(EVENT_ORCHESTRATOR_QUEUE)
        .handle(EventStartCommand.class, (command, headers) -> {
          startEventSyncService.start(command.eventId(), command.source(), command.payloadReference(),
              command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow materializationFlow(RunMaterializationService runMaterializationService) {
    return IntegrationFlow.from(MATERIALIZATION_QUEUE)
        .handle(MaterializationCommand.class, (command, headers) -> {
          runMaterializationService.materialize(command.eventId(), command.plan(), command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow auditFlow(RecordAuditService recordAuditService) {
    return IntegrationFlow.from(AUDIT_QUEUE)
        .handle(DomainEvent.class, (event, headers) -> {
          recordAuditService.record(event);
          return null;
        })
        .get();
  }

  private OrchestrationAcceptedResponse buildAcceptedResponse(OrchestrationCommand command) {
    return new OrchestrationAcceptedResponse(command.eventId(), command.timestamp());
  }
}
