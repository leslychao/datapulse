package io.datapulse.etl.flow.handler;

import com.rabbitmq.client.Channel;
import io.datapulse.etl.EtlFlowHeaders;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.flow.repository.EtlExecutionRepo;
import io.datapulse.etl.flow.repository.EtlSourceStateRepo;
import java.time.Instant;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EtlCompletionHandler {

  private final EtlSourceStateRepo stateRepo;
  private final EtlExecutionRepo execRepo;

  public EtlCompletionHandler(EtlSourceStateRepo stateRepo, EtlExecutionRepo execRepo) {
    this.stateRepo = stateRepo;
    this.execRepo = execRepo;
  }

  @Transactional
  public void markCompletedAndAck(Message<?> msg) {
    EtlSourceExecution work = (EtlSourceExecution) msg.getHeaders().get(EtlFlowHeaders.HDR_WORK);

    stateRepo.markCompleted(work, Instant.now());
    execRepo.incCompletedAndMaybeFinishStopOnFailure(work.requestId());

    ackAfterCommit(msg);
  }

  @Transactional
  public void ackOnly(Message<?> msg) {
    ackAfterCommit(msg);
  }

  private static void ackAfterCommit(Message<?> msg) {
    Channel ch = (Channel) msg.getHeaders().get(AmqpHeaders.CHANNEL);
    Long tag = (Long) msg.getHeaders().get(AmqpHeaders.DELIVERY_TAG);
    if (ch == null || tag == null) return;

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          ch.basicAck(tag, false);
        } catch (Exception ignore) {
        }
      }
    });
  }
}