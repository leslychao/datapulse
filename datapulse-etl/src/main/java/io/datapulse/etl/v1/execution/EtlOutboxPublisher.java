package io.datapulse.etl.v1.execution;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;
import static io.datapulse.etl.v1.flow.core.EtlFlowConstants.CH_ETL_OUTBOX_PUBLISH;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlOutboxPublisher {

  private final EtlExecutionOutboxRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;
  @Qualifier(CH_ETL_OUTBOX_PUBLISH)
  private final MessageChannel outboxPublishChannel;

  @Scheduled(fixedDelayString = "${etl.outbox.fixed-delay-ms:1000}")
  @Transactional
  public void publish() {
    List<EtlExecutionOutboxRepository.OutboxRow> rows = outboxRepository.lockBatch(100);
    outboxPublishChannel.send(MessageBuilder.withPayload(rows).build());
  }

  public void publishRow(EtlExecutionOutboxRepository.OutboxRow row) {
    try {
      rabbitTemplate.convertAndSend(EXCHANGE_EXECUTION_DLX, ROUTING_KEY_EXECUTION_WAIT, row.payloadJson(),
          (MessagePostProcessor) message -> {
            message.getMessageProperties().setExpiration(Long.toString(row.ttlMillis()));
            return message;
          });
      outboxRepository.markSent(row.id());
    } catch (Exception ex) {
      log.warn("Outbox publish failed id={}", row.id(), ex);
      outboxRepository.markFailed(row.id());
    }
  }
}
