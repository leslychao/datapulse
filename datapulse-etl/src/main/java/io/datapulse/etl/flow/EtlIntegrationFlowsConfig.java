package io.datapulse.etl.flow;

import static io.datapulse.etl.EtlExecutionAmqpConstants.*;
import static io.datapulse.etl.EtlTasksAmqpConstants.QUEUE_TASKS;

import io.datapulse.etl.EtlFlowHeaders;
import io.datapulse.etl.dto.StreamItem;
import io.datapulse.etl.flow.handler.EtlCompletionHandler;
import io.datapulse.etl.flow.handler.EtlOrchestratorHandler;
import io.datapulse.etl.flow.handler.EtlWorkerErrorHandler;
import io.datapulse.etl.flow.handler.EtlWorkerPrepareHandler;
import io.datapulse.etl.flow.splitter.SnapshotsRawStreamSplitter;
import java.util.List;
import java.util.function.Function;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;

@Configuration
@EnableIntegration
public class EtlIntegrationFlowsConfig {

  public static final String WORKER_ERROR_CHANNEL = "etlWorkerErrorChannel";

  private final int rawBatchSize = 1000;

  @Bean
  public IntegrationFlow orchestratorFlow(
      ConnectionFactory connectionFactory,
      EtlOrchestratorHandler orchestrator,
      RabbitTemplate etlRabbitTemplate
  ) {
    return IntegrationFlow
        .from(Amqp.inboundAdapter(connectionFactory, QUEUE_TASKS)
            .configureContainer(c -> {
              c.acknowledgeMode(AcknowledgeMode.MANUAL);
              c.prefetchCount(50);
            })
            .errorChannel(WORKER_ERROR_CHANNEL)
        )
        .handle(orchestrator, "handle")
        .split()
        .handle(Amqp.outboundAdapter(etlRabbitTemplate)
            .exchangeName(EXCHANGE_EXECUTION)
            .routingKey(ROUTING_KEY_EXECUTION))
        .get();
  }

  @Bean
  public IntegrationFlow workerFlow(
      ConnectionFactory connectionFactory,
      EtlWorkerPrepareHandler prepare,
      SnapshotsRawStreamSplitter snapshotsRawStreamSplitter,
      EtlRawBatchWriter batchWriter,
      EtlCompletionHandler completion,
      RabbitTemplate etlRabbitTemplate
  ) {
    return IntegrationFlow
        .from(Amqp.inboundAdapter(connectionFactory, QUEUE_EXECUTION)
            .configureContainer(c -> {
              c.acknowledgeMode(AcknowledgeMode.MANUAL);
              c.prefetchCount(50);
            })
            .errorChannel(WORKER_ERROR_CHANNEL)
        )

        .handle(prepare, "prepare")

        // FIX: choose route(Function<...>, Consumer<RouterSpec...>) overload explicitly
        .route((Function<Message<?>, String>) m ->
                (String) m.getHeaders().get(EtlFlowHeaders.HDR_OUTCOME),
            r -> r
                .subFlowMapping(EtlOutcome.SKIP.name(),
                    sf -> sf.handle(completion, "ackOnly")
                )

                .subFlowMapping(EtlOutcome.WAIT.name(), sf -> sf
                    .enrichHeaders(h -> h.headerFunction(
                        AmqpHeaders.EXPIRATION,
                        mm -> String.valueOf(mm.getHeaders().get(EtlFlowHeaders.HDR_TTL_MILLIS))
                    ))
                    .handle(Amqp.outboundAdapter(etlRabbitTemplate)
                        .exchangeName(EXCHANGE_EXECUTION_DLX)
                        .routingKey(ROUTING_KEY_EXECUTION_WAIT))
                    .handle(completion, "ackOnly")
                )

                .defaultSubFlowMapping(sf -> sf
                    .split(snapshotsRawStreamSplitter)

                    .aggregate(a -> a
                        .correlationStrategy(msg ->
                            msg.getHeaders().get(EtlFlowHeaders.HDR_REQUEST_ID) + "|" +
                                msg.getHeaders().get(EtlFlowHeaders.HDR_EVENT) + "|" +
                                msg.getHeaders().get(EtlFlowHeaders.HDR_SOURCE_ID) + "|" +
                                msg.getHeaders().get(EtlFlowHeaders.HDR_RAW_TABLE)
                        )
                        .releaseStrategy(g ->
                            g.size() >= rawBatchSize || StreamItemUtils.containsLast(g.getMessages())
                        )
                        .expireGroupsUponCompletion(true)
                        .sendPartialResultOnExpiry(false)
                        .outputProcessor(g -> {
                          boolean lastBatch = StreamItemUtils.containsLast(g.getMessages());

                          // NOTE: assumes StreamItem is a record like: record StreamItem<T>(T payload, boolean last) {}
                          List<Object> batch = g.getMessages().stream()
                              .map(m -> (StreamItem<?>) m.getPayload())
                              .map(StreamItem::payload) // <-- if your accessor name differs, change here
                              .toList();

                          return new RawBatchEnvelope(batch, lastBatch);
                        })
                    )

                    .handle(batchWriter, "write")

                    // FIX: avoid route(...) ambiguity by using typed overload
                    .route(RawBatchEnvelope.class, RawBatchEnvelope::lastBatch, rr -> rr
                        .subFlowMapping(true, ssf -> ssf.handle(completion, "markCompletedAndAck"))
                        .subFlowMapping(false, ssf -> ssf.nullChannel())
                    )
                )
        )
        .get();
  }

  @Bean
  public IntegrationFlow workerErrorFlow(
      EtlWorkerErrorHandler errorHandler,
      RabbitTemplate etlRabbitTemplate
  ) {
    return IntegrationFlow
        .from(WORKER_ERROR_CHANNEL)
        .filter(p -> p instanceof org.springframework.messaging.support.ErrorMessage)
        .handle(errorHandler, "handle")
        .filter(m -> m != null)

        // FIX: choose route(Function<...>, Consumer<RouterSpec...>) overload explicitly
        .route((Function<Message<?>, String>) m ->
                (String) m.getHeaders().get(EtlFlowHeaders.HDR_OUTCOME),
            r -> r
                .subFlowMapping(EtlOutcome.WAIT.name(), sf -> sf
                    .enrichHeaders(h -> h.headerFunction(
                        AmqpHeaders.EXPIRATION,
                        mm -> String.valueOf(mm.getHeaders().get(EtlFlowHeaders.HDR_TTL_MILLIS))
                    ))
                    .handle(Amqp.outboundAdapter(etlRabbitTemplate)
                        .exchangeName(EXCHANGE_EXECUTION_DLX)
                        .routingKey(ROUTING_KEY_EXECUTION_WAIT))
                )
                .defaultSubFlowMapping(sf -> sf.nullChannel())
        )
        .get();
  }
}