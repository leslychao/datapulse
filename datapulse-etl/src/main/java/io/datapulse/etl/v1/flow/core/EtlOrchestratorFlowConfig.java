package io.datapulse.etl.v1.flow.core;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_TASKS;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.QUEUE_TASKS;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_TASKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.v1.dto.EtlIngestExecutionContext;
import io.datapulse.etl.v1.dto.EtlRunRequest;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import io.datapulse.etl.v1.dto.RunTask;
import io.datapulse.etl.v1.execution.EtlExecutionWorkerTxService;
import io.datapulse.etl.v1.execution.EtlTaskOrchestratorTxService;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EtlOrchestratorFlowConfig {

  private final RabbitTemplate rabbitTemplate;
  private final MessageConverter messageConverter;
  private final ObjectMapper objectMapper;

  private final EtlOrchestrationCommandFactory commandFactory;
  private final EtlTaskOrchestratorTxService orchestratorTxService;
  private final EtlExecutionWorkerTxService workerTxService;
  private final EtlSnapshotIngestionFlowConfig ingestFlowConfig;

  @Bean(name = EtlFlowConstants.CH_ETL_TASKS)
  public MessageChannel etlTasksChannel() {
    return new DirectChannel();
  }

  @Bean(name = EtlFlowConstants.CH_ETL_EXECUTION)
  public MessageChannel etlExecutionChannel() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow etlHttpInboundFlow() {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestPayloadType(EtlRunRequest.class)
            .statusCodeFunction(message -> HttpStatus.ACCEPTED))
        .transform(EtlRunRequest.class, commandFactory::toRunTasks)
        .publishSubscribeChannel(s -> s
            .subscribe(f -> f
                .split()
                .handle(Amqp.outboundAdapter(rabbitTemplate)
                        .exchangeName(EXCHANGE_TASKS)
                        .routingKey(ROUTING_KEY_TASKS),
                    e -> e.requiresReply(false))
            )
        )
        .transform(p -> Map.of("status", "accepted"))
        .get();
  }

  @Bean
  public IntegrationFlow etlTasksInboundFlow(ConnectionFactory connectionFactory) {
    return IntegrationFlow
        .from(
            Amqp.inboundAdapter(connectionFactory, QUEUE_TASKS)
                .messageConverter(messageConverter)
        )
        .channel(EtlFlowConstants.CH_ETL_TASKS)
        .get();
  }

  @Bean
  public IntegrationFlow etlTasksFlow() {
    return IntegrationFlow
        .from(EtlFlowConstants.CH_ETL_TASKS)
        .transform(payload -> {
          if (payload instanceof RunTask rt) return rt;
          return objectMapper.convertValue(payload, RunTask.class);
        })
        .handle(RunTask.class, (task, headers) -> {
          try {
            orchestratorTxService.orchestrate(task);
            return null;
          } catch (io.datapulse.domain.exception.NotFoundException e) {
            // важно: лог + контекст
            log.warn("Non-retryable ETL task rejected (account not found). task={}", task, e);

            // ключевое: НЕ REQUEUE
            throw new AmqpRejectAndDontRequeueException(
                "Account not found for task; rejecting without requeue", e
            );
          }
        }, e -> e.requiresReply(false))
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionInboundFlow(ConnectionFactory connectionFactory) {
    return IntegrationFlow
        .from(
            Amqp.inboundAdapter(connectionFactory, QUEUE_EXECUTION)
                .messageConverter(messageConverter)
        )
        .channel(EtlFlowConstants.CH_ETL_EXECUTION)
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionFlow() {
    return IntegrationFlow
        .from(EtlFlowConstants.CH_ETL_EXECUTION)
        .transform(payload -> {
          if (payload instanceof EtlSourceExecution ex) {
            return ex;
          }
          return objectMapper.convertValue(payload, EtlSourceExecution.class);
        })
        .handle(workerTxService, "prepareIngest")
        .filter(Objects::nonNull)
        .gateway(ingestFlowConfig.ingestSubflow())
        .handle(EtlIngestExecutionContext.class, (ctx, headers) -> {
          workerTxService.finalizeIngest(ctx);
          return null;
        }, e -> e.requiresReply(false))
        .get();
  }
}
