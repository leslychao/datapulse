package io.datapulse.etl.flow.core;

import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;
import static io.datapulse.etl.config.EtlExecutionRabbitConfig.DEFAULT_WAIT_TTL_MILLIS;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_EXECUTION_DISPATCH;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_EXECUTION_INBOUND;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_EXECUTION_PROCESS;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_EXECUTION_RESULT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_EXECUTION_WAIT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EXECUTION;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_SOURCE_ID;

import io.datapulse.domain.SyncStatus;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionResult;
import io.datapulse.etl.dto.IngestResult;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.dto.OrchestrationBundle;
import io.datapulse.etl.handler.error.EtlIngestErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlExecutionFlowConfig {

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final MessageConverter etlExecutionMessageConverter;
  private final MarketplaceExecutionChannelProvider marketplaceExecutionChannelProvider;
  private final EtlIngestErrorHandler ingestErrorHandler;
  private final OrchestrationAggregationHelper aggregationHelper;

  @Bean(name = CH_ETL_EXECUTION_DISPATCH)
  public MessageChannel executionDispatchChannel() {
    return new org.springframework.integration.channel.DirectChannel();
  }

  @Bean(name = CH_ETL_EXECUTION_INBOUND)
  public MessageChannel executionInboundChannel() {
    return new org.springframework.integration.channel.DirectChannel();
  }

  @Bean(name = CH_ETL_EXECUTION_PROCESS)
  public MessageChannel executionProcessChannel() {
    return new org.springframework.integration.channel.DirectChannel();
  }

  @Bean(name = CH_ETL_EXECUTION_RESULT)
  public MessageChannel executionResultChannel() {
    return new org.springframework.integration.channel.DirectChannel();
  }

  @Bean(name = CH_ETL_EXECUTION_WAIT)
  public MessageChannel executionWaitChannel() {
    return new org.springframework.integration.channel.DirectChannel();
  }

  @Bean
  public IntegrationFlow etlExecutionDispatchOutboundFlow() {
    return IntegrationFlow
        .from(CH_ETL_EXECUTION_DISPATCH)
        .handle(
            Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                .exchangeName(EXCHANGE_EXECUTION)
                .routingKey(ROUTING_KEY_EXECUTION),
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionInboundFlow(ConnectionFactory connectionFactory) {
    return IntegrationFlow
        .from(
            Amqp.inboundAdapter(connectionFactory, QUEUE_EXECUTION)
                .messageConverter(etlExecutionMessageConverter)
                .configureContainer(container -> container
                    .concurrentConsumers(4)
                    .maxConcurrentConsumers(8)
                    .prefetchCount(1)
                    .defaultRequeueRejected(false)
                )
        )
        .channel(CH_ETL_EXECUTION_INBOUND)
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionSequencingFlow() {
    return IntegrationFlow
        .from(CH_ETL_EXECUTION_INBOUND)
        .handle(EtlSourceExecution.class, (execution, headers) -> {
          String marketplaceKey = execution.marketplace().name();
          MessageChannel marketplaceChannel = marketplaceExecutionChannelProvider
              .resolveForMarketplace(marketplaceKey);
          marketplaceChannel.send(
              MessageBuilder.withPayload(execution).copyHeaders(headers).build());
          return null;
        }, endpoint -> endpoint.requiresReply(false))
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionProcessingFlow() {
    return IntegrationFlow
        .from(CH_ETL_EXECUTION_PROCESS)
        .enrichHeaders(headers -> headers
            .headerFunction(HDR_ETL_EXECUTION, Message::getPayload)
        )
        .gateway(CH_ETL_INGEST, g -> g
            .requestTimeout(0L)
            .replyTimeout(-1L)
            .advice(executionErrorAdvice())
        )
        .handle((payload, headers) -> new ExecutionResult(
            headers.get(HDR_ETL_EXECUTION, EtlSourceExecution.class),
            payload instanceof IngestResult ingest
                ? ingest
                : new IngestResult(
                    headers.get(HDR_ETL_SOURCE_ID, String.class),
                    IngestStatus.SUCCESS,
                    null,
                    null,
                    null
                )
        ))
        .route(
            ExecutionResult.class,                                   // явный тип payload
            result -> result.ingestResult().isWait() ? "WAIT" : "FINAL", // routing key (String)
            spec -> spec
                .subFlowMapping("WAIT", sf -> sf.channel(CH_ETL_EXECUTION_WAIT))
                .subFlowMapping("FINAL", sf -> sf.channel(CH_ETL_EXECUTION_RESULT))
        )
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionWaitFlow() {
    return IntegrationFlow
        .from(CH_ETL_EXECUTION_WAIT)
        .enrichHeaders(headers -> headers
            .headerFunction(AmqpHeaders.EXPIRATION, message -> {
              ExecutionResult result = (ExecutionResult) message.getPayload();
              Integer retryAfter = result.ingestResult().retryAfterSeconds();
              long waitSeconds = retryAfter != null
                  ? Math.max(1L, retryAfter)
                  : Math.max(1L, DEFAULT_WAIT_TTL_MILLIS / 1000L);
              return Long.toString(waitSeconds * 1000L);
            })
        )
        .transform(ExecutionResult.class, ExecutionResult::execution)
        .handle(
            Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                .exchangeName(EXCHANGE_EXECUTION_DLX)
                .routingKey(ROUTING_KEY_EXECUTION_WAIT),
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  @Bean
  public IntegrationFlow etlExecutionResultAggregationFlow() {
    return IntegrationFlow
        .from(CH_ETL_EXECUTION_RESULT)
        .aggregate(aggregator -> aggregator
            .correlationStrategy(m -> m.getHeaders().get(HDR_ETL_REQUEST_ID))
            .releaseStrategy(aggregationHelper::isFullGroup)
            .sendPartialResultOnExpiry(true)
            .expireGroupsUponCompletion(true)
            .outputProcessor(aggregationHelper::buildBundle)
        )
        .filter(OrchestrationBundle.class, bundle -> bundle.syncStatus() != SyncStatus.WAIT)
        .channel(CH_ETL_ORCHESTRATION_RESULT)
        .get();
  }

  @Bean
  public AbstractRequestHandlerAdvice executionErrorAdvice() {
    return new AbstractRequestHandlerAdvice() {
      @Override
      protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
        try {
          return callback.execute();
        } catch (Exception ex) {
          return ingestErrorHandler.handleIngestError(ex, message);
        }
      }
    };
  }

  @ServiceActivator(inputChannel = CH_ETL_EXECUTION_RESULT)
  public void logExecutionResult(Message<?> message) {
    MessageHeaders headers = message.getHeaders();
    ExecutionResult payload = (ExecutionResult) message.getPayload();
    log.info(
        "Execution finished: requestId={}, marketplace={}, sourceId={}, status={}",
        headers.get(HDR_ETL_REQUEST_ID),
        payload.execution().marketplace(),
        payload.execution().sourceId(),
        payload.ingestResult().status()
    );
  }
}
