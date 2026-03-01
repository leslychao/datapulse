package io.datapulse.etl.flow.handler;


import static io.datapulse.etl.EtlFlowHeaders.HDR_OUTCOME;
import static io.datapulse.etl.EtlFlowHeaders.HDR_TTL_MILLIS;
import static io.datapulse.etl.EtlFlowHeaders.HDR_WORK;

import com.rabbitmq.client.Channel;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.flow.EtlOutcome;
import io.datapulse.etl.flow.repository.EtlExecutionRepo;
import io.datapulse.etl.flow.repository.EtlSourceStateRepo;
import io.datapulse.etl.flow.repository.EtlSourceStateRepo.SourceState;
import io.datapulse.marketplaces.resilience.LocalRateLimitBackoffRequiredException;
import io.datapulse.marketplaces.resilience.TooManyRequestsBackoffRequiredException;
import java.time.Instant;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.integration.support.ErrorMessageUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EtlWorkerErrorHandler {

  private final EtlSourceStateRepo stateRepo;
  private final EtlExecutionRepo execRepo;

  private final long minBackoffMillis = 1_000L;
  private final long maxBackoffMillis = 60_000L;

  public EtlWorkerErrorHandler(EtlSourceStateRepo stateRepo, EtlExecutionRepo execRepo) {
    this.stateRepo = stateRepo;
    this.execRepo = execRepo;
  }

  @Transactional
  public Message<?> handle(ErrorMessage errorMessage) {
    Message<?> failed = ErrorMessageUtils.getOriginalMessage(errorMessage);
    if (failed == null) return null;

    EtlSourceExecution work = (EtlSourceExecution) failed.getPayload();
    Throwable cause = unwrap(errorMessage);

    if (cause instanceof TooManyRequestsBackoffRequiredException tm) {
      return scheduleWaitAndAck(failed, work, tm.getRetryAfterSeconds(), "HTTP_429_TOO_MANY_REQUESTS", cause);
    }
    if (cause instanceof LocalRateLimitBackoffRequiredException lr) {
      return scheduleWaitAndAck(failed, work, lr.getRetryAfterSeconds(), "LOCAL_RATE_LIMIT", cause);
    }

    stateRepo.markFailedTerminal(work, cause.getClass().getSimpleName(), safeMsg(cause));
    execRepo.incFailedAndMaybeFinishStopOnFailure(work.requestId());

    ackAfterCommit(failed);

    return MessageBuilder
        .withPayload(work)
        .copyHeaders(failed.getHeaders())
        .setHeader(HDR_WORK, work)
        .setHeader(HDR_OUTCOME, EtlOutcome.TERMINAL_FAIL.name())
        .build();
  }

  private Message<?> scheduleWaitAndAck(
      Message<?> failed,
      EtlSourceExecution work,
      int retryAfterSeconds,
      String reasonCode,
      Throwable cause
  ) {
    long ttlMs = clamp((long) retryAfterSeconds * 1000L, minBackoffMillis, maxBackoffMillis);

    SourceState st = stateRepo.load(work.requestId(), work.event(), work.sourceId());
    int nextAttempt = st.attempt() + 1;

    if (nextAttempt > st.maxAttempts()) {
      stateRepo.markFailedTerminal(work, "MAX_ATTEMPTS_EXCEEDED", "Max attempts exceeded on backoff");
      execRepo.incFailedAndMaybeFinishStopOnFailure(work.requestId());

      ackAfterCommit(failed);

      return MessageBuilder
          .withPayload(work)
          .copyHeaders(failed.getHeaders())
          .setHeader(HDR_WORK, work)
          .setHeader(HDR_OUTCOME, EtlOutcome.TERMINAL_FAIL.name())
          .build();
    }

    boolean scheduled = stateRepo.casScheduleRetry(
        work,
        st.attempt(),
        nextAttempt,
        Instant.now().plusMillis(ttlMs),
        reasonCode,
        safeMsg(cause)
    );

    ackAfterCommit(failed);

    EtlOutcome out = scheduled ? EtlOutcome.WAIT : EtlOutcome.SKIP;

    MessageBuilder<?> b = MessageBuilder
        .withPayload(work)
        .copyHeaders(failed.getHeaders())
        .setHeader(HDR_WORK, work)
        .setHeader(HDR_OUTCOME, out.name());

    if (scheduled) {
      b.setHeader(HDR_TTL_MILLIS, ttlMs);
    }

    return b.build();
  }

  private static Throwable unwrap(ErrorMessage em) {
    Throwable t = em.getPayload();
    if (t instanceof MessagingException me && me.getCause() != null) return me.getCause();
    return t;
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

  private static long clamp(long v, long min, long max) {
    return Math.max(min, Math.min(max, v));
  }

  private static String safeMsg(Throwable t) {
    String m = t.getMessage();
    return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
  }
}