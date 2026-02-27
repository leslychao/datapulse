package io.datapulse.etl.v1.flow.core;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;

import io.datapulse.etl.v1.execution.EtlExecutionOutboxRepository;
import io.datapulse.etl.v1.execution.EtlExecutionOutboxRepository.OutboxRow;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.ErrorMessage;

@Configuration
@RequiredArgsConstructor
public class EtlOutboxFlowConfig {

  private static final String HDR_OUTBOX_ID = "ETL_OUTBOX_ID";
  private static final String CH_ETL_OUTBOX_ERROR = "ETL_OUTBOX_ERROR";

  private final EtlExecutionOutboxRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;

  @Bean(name = CH_ETL_OUTBOX_ERROR)
  public MessageChannel etlOutboxErrorChannel() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow etlOutboxPublishFlow(
      @Value("${etl.outbox.fixed-delay-ms:1000}") long fixedDelayMs,
      @Value("${etl.outbox.batch-size:100}") int batchSize
  ) {
    return IntegrationFlow
        .fromSupplier(
            () -> outboxRepository.lockBatch(batchSize),
            source -> source.poller(
                Pollers.fixedDelay(fixedDelayMs)
                    .maxMessagesPerPoll(1)
                    .errorChannel(CH_ETL_OUTBOX_ERROR)
            )
        )
        .handle(List.class, (rows, headers) -> rows.isEmpty() ? null : rows)
        .filter(Objects::nonNull)
        .split(List.class, List::stream)
        .enrichHeaders(headers -> headers
            .headerFunction(HDR_OUTBOX_ID, message -> ((OutboxRow) message.getPayload()).id())
            .headerFunction(
                AmqpHeaders.EXPIRATION,
                message -> Long.toString(((OutboxRow) message.getPayload()).ttlMillis())
            )
        )
        .transform(OutboxRow.class, OutboxRow::payloadJson)
        .handle(
            Amqp.outboundAdapter(rabbitTemplate)
                .exchangeName(EXCHANGE_EXECUTION_DLX)
                .routingKey(ROUTING_KEY_EXECUTION_WAIT),
            endpoint -> endpoint.requiresReply(false)
        )
        .handle(
            String.class,
            (payload, headers) -> {
              Long outboxId = headers.get(HDR_OUTBOX_ID, Long.class);
              if (outboxId != null) {
                outboxRepository.markSent(outboxId);
              }
              return null;
            },
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  @Bean
  public IntegrationFlow etlOutboxErrorFlow() {
    return IntegrationFlow
        .from(CH_ETL_OUTBOX_ERROR)
        .handle(
            ErrorMessage.class,
            (errorMessage, headers) -> {
              Throwable throwable = errorMessage.getPayload();
              if (throwable instanceof MessageHandlingException handlingException
                  && handlingException.getFailedMessage() != null) {
                Long outboxId = handlingException.getFailedMessage().getHeaders()
                    .get(HDR_OUTBOX_ID, Long.class);
                if (outboxId != null) {
                  outboxRepository.markFailed(outboxId);
                }
              }
              return null;
            },
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }
}
