package io.datapulse.etl.v1.execution;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlOutboxPublisher {

  private final EtlExecutionOutboxRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;

  @Scheduled(fixedDelayString = "${etl.outbox.fixed-delay-ms:1000}")
  @Transactional
  public void publish() {
    for (var row : outboxRepository.lockBatch(100)) {
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
}
