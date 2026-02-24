package io.datapulse.etl.v1.flow.core;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_TASKS;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.QUEUE_TASKS;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_TASKS;

import io.datapulse.etl.v1.dto.EtlIngestExecutionContext;
import io.datapulse.etl.v1.dto.EtlRunRequest;
import io.datapulse.etl.v1.dto.RunTask;
import io.datapulse.etl.v1.execution.EtlExecutionPayloadCodec;
import io.datapulse.etl.v1.execution.EtlExecutionWorkerTxService;
import io.datapulse.etl.v1.execution.EtlTaskOrchestratorTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
  private final EtlOrchestrationCommandFactory commandFactory;
  private final EtlExecutionPayloadCodec codec;
  private final EtlTaskOrchestratorTxService orchestratorTxService;
  private final EtlExecutionWorkerTxService workerTxService;

  // ingest subflow (без фасадов/каналов) — внедряем как bean
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
        .split()
        .handle(Amqp.outboundAdapter(rabbitTemplate).exchangeName(EXCHANGE_TASKS).routingKey(ROUTING_KEY_TASKS),
            endpoint -> endpoint.requiresReply(false))
        .handle(payload -> java.util.Map.of("status", "accepted"))
        .get();
  }

  @Bean
  public IntegrationFlow etlTasksInboundFlow(ConnectionFactory connectionFactory) {
    return IntegrationFlow
        .from(Amqp.inboundAdapter(connectionFactory, QUEUE_TASKS))
        .channel(EtlFlowConstants.CH_ETL_TASKS)
        .get();
  }

  @Bean
  public IntegrationFlow etlTasksFlow() {
    return IntegrationFlow
        .from(EtlFlowConstants.CH_ETL_TASKS)
        .filter(byte[].class, payload -> codec.parseRunTask(payload).isPresent())
        .transform(byte[].class, payload -> codec.parseRunTask(payload).orElseThrow())
        .handle(RunTask.class, (task, headers) -> {
          orchestratorTxService.orchestrate(task);
          return null;
        }, endpoint -> endpoint.requiresReply(false))
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionInboundFlow(ConnectionFactory connectionFactory) {
    return IntegrationFlow
        .from(Amqp.inboundAdapter(connectionFactory, QUEUE_EXECUTION))
        .channel(EtlFlowConstants.CH_ETL_EXECUTION)
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionFlow() {
    return IntegrationFlow
        .from(EtlFlowConstants.CH_ETL_EXECUTION)
        .filter(byte[].class, payload -> {
          boolean valid = codec.parseExecution(payload).isPresent();
          if (!valid) {
            log.warn("Invalid execution payload received; dropping message");
          }
          return valid;
        })
        .transform(byte[].class, payload -> codec.parseExecution(payload).orElseThrow())

        // 1) prepare TX: mark in-progress + delete raw + build ctx
        .handle(workerTxService, "prepareIngest")
        .filter(p -> p != null)

        // 2) ingest subflow (request–reply): returns SAME EtlIngestExecutionContext on completion
        .gateway(ingestFlowConfig.ingestSubflow())

        // 3) finalize TX: mark completed + resolve execution
        .handle(EtlIngestExecutionContext.class, (ctx, headers) -> {
          workerTxService.finalizeIngest(ctx);
          return null;
        }, endpoint -> endpoint.requiresReply(false))

        .get();
  }
}
