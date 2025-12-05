package io.datapulse.etl.flow.execution;

import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;
import static io.datapulse.etl.config.EtlExecutionRabbitConfig.DEFAULT_WAIT_TTL_MILLIS;
import static io.datapulse.etl.flow.core.FlowChannels.CH_EXECUTION_CORE;
import static io.datapulse.etl.flow.core.FlowChannels.CH_EXECUTION_INBOUND;
import static io.datapulse.etl.flow.core.FlowChannels.CH_EXECUTION_RESULT;
import static io.datapulse.etl.flow.core.FlowChannels.CH_EVENT_AUDIT;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_EVENT_AGGREGATION;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_RETRY_AFTER;
import static org.springframework.amqp.support.AmqpHeaders.EXPIRATION;

import io.datapulse.etl.flow.core.model.EventAggregation;
import io.datapulse.etl.flow.core.model.ExecutionDescriptor;
import io.datapulse.etl.flow.core.model.ExecutionOutcome;
import io.datapulse.etl.flow.core.model.ExecutionStatus;
import io.datapulse.etl.flow.core.registry.ExecutionRegistry;
import io.datapulse.etl.flow.snapshot.SnapshotIngestionPipeline;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class ExecutionFlowConfiguration {

  private final SnapshotIngestionPipeline snapshotIngestionPipeline;
  private final ExecutionRegistry executionRegistry;
  private final RabbitTemplate etlExecutionRabbitTemplate;

  public ExecutionFlowConfiguration(
      SnapshotIngestionPipeline snapshotIngestionPipeline,
      ExecutionRegistry executionRegistry,
      RabbitTemplate etlExecutionRabbitTemplate
  ) {
    this.snapshotIngestionPipeline = snapshotIngestionPipeline;
    this.executionRegistry = executionRegistry;
    this.etlExecutionRabbitTemplate = etlExecutionRabbitTemplate;
  }

  @Bean(name = CH_EXECUTION_INBOUND)
  public MessageChannel executionInboundChannel() {
    return MessageChannels.executor(new SimpleAsyncTaskExecutor("execution-inbound-"))
        .getObject();
  }

  @Bean(name = CH_EXECUTION_CORE)
  public MessageChannel executionCoreChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_EXECUTION_RESULT)
  public MessageChannel executionResultChannel() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow executionInboundFlow(
      ConnectionFactory connectionFactory,
      MessageConverter etlExecutionMessageConverter
  ) {
    return IntegrationFlow.from(
            Amqp.inboundAdapter(connectionFactory, QUEUE_EXECUTION)
                .messageConverter(etlExecutionMessageConverter)
                .configureContainer(container -> container
                    .concurrentConsumers(1)
                    .maxConcurrentConsumers(1)
                    .prefetchCount(1)
                )
        )
        .channel(CH_EXECUTION_INBOUND)
        .get();
  }

  @Bean
  public IntegrationFlow executionFlow() {
    return IntegrationFlow.from(CH_EXECUTION_INBOUND)
        .channel(CH_EXECUTION_CORE)
        .transform(ExecutionDescriptor.class, snapshotIngestionPipeline::ingest)
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_EVENT_AGGREGATION,
            message -> executionRegistry.update((ExecutionOutcome) message.getPayload())
        ))
        .wireTap(tap -> tap
            .filter(Message.class, m -> m.getHeaders().containsKey(HDR_EVENT_AGGREGATION))
            .transform(Message.class, m -> m.getHeaders().get(HDR_EVENT_AGGREGATION, EventAggregation.class))
            .channel(CH_EVENT_AUDIT)
        )
        .route(
            ExecutionOutcome.class,
            outcome -> outcome.status() == ExecutionStatus.WAITING ? "WAIT" : "RESULT",
            mapping -> mapping
                .subFlowMapping("WAIT", sf -> sf
                    .enrichHeaders(headers -> headers
                        .headerFunction(
                            HDR_RETRY_AFTER,
                            message -> ((ExecutionOutcome) message.getPayload())
                                .retryAfterSecondsOptional()
                                .orElse(null)
                        )
                        .headerFunction(
                            EXPIRATION,
                            message -> computeTtl((ExecutionOutcome) message.getPayload())
                        )
                    )
                    .transform(ExecutionOutcome.class, ExecutionOutcome::descriptor)
                    .handle(Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                        .exchangeName(EXCHANGE_EXECUTION_DLX)
                        .routingKey(ROUTING_KEY_EXECUTION_WAIT),
                        endpoint -> endpoint.requiresReply(false))
                )
                .subFlowMapping("RESULT", sf -> sf
                    .channel(CH_EXECUTION_RESULT)
                )
        )
        .get();
  }

  private String computeTtl(ExecutionOutcome outcome) {
    long waitSeconds = outcome.retryAfterSecondsOptional()
        .map(Integer::longValue)
        .filter(value -> value > 0)
        .orElse(DEFAULT_WAIT_TTL_MILLIS / 1000L);
    return Long.toString(Math.max(waitSeconds, 1L) * 1000L);
  }
}
