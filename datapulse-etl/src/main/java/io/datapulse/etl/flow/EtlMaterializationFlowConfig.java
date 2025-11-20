package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_EVENT_INGEST_COMPLETED;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_EVENT_MATERIALIZED;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EXPECTED_SNAPSHOTS;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.etl.service.materialization.EtlMaterializationService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class EtlMaterializationFlowConfig {

  private final EtlMaterializationService etlMaterializationService;

  @Bean(name = CH_ETL_EVENT_INGEST_COMPLETED)
  public MessageChannel eventIngestCompletedChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_ETL_EVENT_MATERIALIZED)
  public MessageChannel eventMaterializedChannel() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow etlEventIngestCompletionFlow() {
    return IntegrationFlow
        .from(CH_ETL_EVENT_INGEST_COMPLETED)
        .log(m -> "Snapshot ingest completed tick: snapshotId=" + m.getPayload())
        .aggregate(a -> a
            .correlationStrategy(message -> buildCorrelationKey(message.getHeaders()))
            .releaseStrategy(this::isGroupComplete)
            .expireGroupsUponCompletion(true)
        )
        .handle(Message.class, (message, headers) -> {
          String requestId = getRequiredHeader(headers, HDR_ETL_REQUEST_ID, String.class);
          Long accountId = getRequiredHeader(headers, HDR_ETL_ACCOUNT_ID, Long.class);
          MarketplaceEvent event = getRequiredEvent(headers);

          LocalDate from = getRequiredHeader(headers, HDR_ETL_DATE_FROM, LocalDate.class);
          LocalDate to = getRequiredHeader(headers, HDR_ETL_DATE_TO, LocalDate.class);

          log.info(
              "{} ETL materialization started: from={}, to={}",
              logPrefix(headers),
              from,
              to
          );

          etlMaterializationService.materialize(
              accountId,
              event,
              from,
              to,
              requestId
          );

          log.info(
              "{} ETL materialization finished",
              logPrefix(headers)
          );

          return requestId;
        })
        .channel(CH_ETL_EVENT_MATERIALIZED)
        .get();
  }

  @Bean
  public IntegrationFlow etlEventMaterializedLoggingFlow() {
    return IntegrationFlow
        .from(CH_ETL_EVENT_MATERIALIZED)
        .log(m -> "ETL event materialized: " + m.getPayload())
        .nullChannel();
  }

  private boolean isGroupComplete(MessageGroup group) {
    Message<?> any = group.getOne();
    if (any == null) {
      return false;
    }

    MessageHeaders headers = any.getHeaders();

    int expected = getRequiredHeader(headers, HDR_ETL_EXPECTED_SNAPSHOTS, Integer.class);
    if (expected <= 0) {
      throw new IllegalStateException(
          logPrefix(headers) + " header " + HDR_ETL_EXPECTED_SNAPSHOTS +
              " must be > 0, actual=" + expected
      );
    }

    int size = group.size();
    if (size > expected) {
      log.warn(
          "{} too many ingest-completed ticks: expected={}, actual={}",
          logPrefix(headers),
          expected,
          size
      );
    }

    return size >= expected;
  }

  private String buildCorrelationKey(MessageHeaders headers) {
    String requestId = getRequiredHeader(headers, HDR_ETL_REQUEST_ID, String.class);
    Long accountId = getRequiredHeader(headers, HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = getRequiredHeader(headers, HDR_ETL_EVENT, String.class);
    return requestId + "|" + accountId + "|" + eventValue;
  }

  private MarketplaceEvent getRequiredEvent(MessageHeaders headers) {
    String eventValue = getRequiredHeader(headers, HDR_ETL_EVENT, String.class);
    MarketplaceEvent event = MarketplaceEvent.fromString(eventValue);
    if (event == null) {
      throw new IllegalStateException(
          "Unknown marketplace event in header " + HDR_ETL_EVENT + ": " + eventValue
      );
    }
    return event;
  }

  private <T> T getRequiredHeader(MessageHeaders headers, String name, Class<T> type) {
    T value = headers.get(name, type);
    if (value == null) {
      throw new IllegalStateException("Missing required header: " + name);
    }
    return value;
  }

  private String logPrefix(MessageHeaders headers) {
    String requestId = getRequiredHeader(headers, HDR_ETL_REQUEST_ID, String.class);
    Long accountId = getRequiredHeader(headers, HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = getRequiredHeader(headers, HDR_ETL_EVENT, String.class);
    return "[requestId=%s, accountId=%d, event=%s]".formatted(
        requestId,
        accountId,
        eventValue
    );
  }
}
