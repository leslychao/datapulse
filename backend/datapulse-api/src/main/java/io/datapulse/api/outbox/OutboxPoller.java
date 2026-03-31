package io.datapulse.api.outbox;

import io.datapulse.platform.outbox.OutboxEvent;
import io.datapulse.platform.outbox.OutboxEventPollerRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "datapulse.outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OutboxPollerProperties.class)
public class OutboxPoller {

  private final OutboxEventPollerRepository pollerRepository;
  private final OutboxEventPublisher publisher;
  private final OutboxPollerProperties properties;
  private final TransactionTemplate transactionTemplate;

  @Scheduled(fixedDelayString = "${datapulse.outbox.poll-interval:PT1S}")
  @SchedulerLock(name = "outbox-poller", lockAtMostFor = "PT5M")
  public void poll() {
    try {
      transactionTemplate.executeWithoutResult(status -> doPoll());
    } catch (Exception e) {
      log.error("Outbox poller cycle failed", e);
    }
  }

  private void doPoll() {
    List<String> eventTypeNames = properties.getRuntime().getEventTypes().stream()
        .map(OutboxEventType::name)
        .toList();

    if (eventTypeNames.isEmpty()) {
      return;
    }

    List<OutboxEvent> events = pollerRepository.findEventsForPublish(
        eventTypeNames,
        properties.getBatchSize(),
        properties.getMaxRetryCount()
    );

    if (events.isEmpty()) {
      return;
    }

    log.info("Outbox poller picked up events: count={}", events.size());

    List<Long> publishedIds = new ArrayList<>();

    for (OutboxEvent event : events) {
      try {
        publisher.publish(event);
        publishedIds.add(event.getId());
      } catch (Exception e) {
        log.warn("Failed to publish outbox event: id={}, type={}, retryCount={}, error={}",
            event.getId(), event.getEventType(), event.getRetryCount(), e.getMessage(), e);

        OffsetDateTime nextRetry = OffsetDateTime.now()
            .plus(properties.getRetryBackoff().multipliedBy(event.getRetryCount() + 1));
        pollerRepository.markFailed(event.getId(), nextRetry);
      }
    }

    if (!publishedIds.isEmpty()) {
      pollerRepository.markPublished(publishedIds);
      log.info("Outbox events published: count={}", publishedIds.size());
    }
  }
}
