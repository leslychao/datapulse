package io.datapulse.etl.integration.config;

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
import io.datapulse.etl.integration.messaging.EventStartCommand;
import io.datapulse.etl.integration.messaging.ExecutionCommand;
import io.datapulse.etl.integration.messaging.MaterializationCommand;
import io.datapulse.etl.integration.messaging.RetryCommand;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;

@Configuration
public class EtlIntegrationFlows {

  @Bean
  public IntegrationFlow executionWorkerFlow(ProcessExecutionService processExecutionService) {
    return IntegrationFlows.from(EXECUTION_WORKER_QUEUE)
        .handle(ExecutionCommand.class, (command, headers) -> {
          processExecutionService.process(command.executionId(), command.plan(), command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow retryWaitFlow(ProcessRetryTimeoutService processRetryTimeoutService) {
    return IntegrationFlows.from(RETRY_WAIT_QUEUE)
        .handle(RetryCommand.class, (command, headers) -> {
          processRetryTimeoutService.processRetry(command.executionId(), command.request(),
              command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow eventOrchestratorFlow(StartEventSyncService startEventSyncService) {
    return IntegrationFlows.from(EVENT_ORCHESTRATOR_QUEUE)
        .handle(EventStartCommand.class, (command, headers) -> {
          startEventSyncService.start(command.eventId(), command.source(), command.payloadReference(),
              command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow materializationFlow(RunMaterializationService runMaterializationService) {
    return IntegrationFlows.from(MATERIALIZATION_QUEUE)
        .handle(MaterializationCommand.class, (command, headers) -> {
          runMaterializationService.materialize(command.eventId(), command.plan(), command.timestamp());
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow auditFlow(RecordAuditService recordAuditService) {
    return IntegrationFlows.from(AUDIT_QUEUE)
        .handle(DomainEvent.class, (event, headers) -> {
          recordAuditService.record(event);
          return null;
        })
        .get();
  }
}
