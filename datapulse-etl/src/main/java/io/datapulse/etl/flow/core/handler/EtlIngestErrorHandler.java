package io.datapulse.etl.flow.core.handler;

import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;

import io.datapulse.core.i18n.I18nMessageService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.marketplaces.resilience.TooManyRequestsBackoffRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlIngestErrorHandler {

  private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private final I18nMessageService i18nMessageService;

  public ExecutionOutcome handleIngestError(
      Throwable error,
      Message<?> message
  ) {
    MessageHeaders headers = message.getHeaders();

    String requestId = resolveRequestId(headers);
    Object payload = message.getPayload();

    long accountId = 0L;
    String sourceId = null;
    MarketplaceType marketplace = null;
    MarketplaceEvent event = null;

    if (payload instanceof EtlSourceExecution execution) {
      accountId = execution.accountId();
      sourceId = execution.sourceId();
      marketplace = execution.marketplace();
      event = execution.event();
    }

    IngestStatus status = determineStatus(error);

    String errorClass = error.getClass().getName();
    String userErrorMessage = i18nMessageService.userMessage(error);
    String safeErrorMessage = truncate(userErrorMessage);
    Integer retryAfterSeconds = resolveRetryAfterSeconds(error);
    Long retryAfterMillis = retryAfterSeconds == null ? null : retryAfterSeconds * 1_000L;

    log.warn(
        "ETL ingest failed: requestId={}, accountId={}, event={}, marketplace={}, sourceId={}, status={}, errorClass={}, errorMessage={}",
        requestId,
        accountId,
        event,
        marketplace,
        sourceId,
        status,
        errorClass,
        safeErrorMessage
    );
    if (log.isDebugEnabled()) {
      log.debug(
          "ETL ingest failed with full stacktrace: requestId={}, accountId={}, event={}, marketplace={}, sourceId={}, status={}, errorClass={}",
          requestId,
          accountId,
          event,
          marketplace,
          sourceId,
          status,
          errorClass,
          error
      );
    }

    return new ExecutionOutcome(
        requestId,
        accountId,
        sourceId,
        marketplace,
        event,
        status,
        0L,
        safeErrorMessage,
        retryAfterMillis
    );
  }

  private String resolveRequestId(MessageHeaders headers) {
    String value = headers.get(HDR_ETL_REQUEST_ID, String.class);
    return value != null ? value : "";
  }

  private IngestStatus determineStatus(Throwable error) {
    return isBackoffRequired(error) ? IngestStatus.WAITING_RETRY : IngestStatus.FAILED;
  }

  private boolean isBackoffRequired(Throwable error) {
    if (error instanceof TooManyRequestsBackoffRequiredException) {
      return true;
    }

    Throwable current = error.getCause();
    while (current != null) {
      if (current instanceof TooManyRequestsBackoffRequiredException) {
        return true;
      }
      current = current.getCause();
    }

    return false;
  }

  private Integer resolveRetryAfterSeconds(Throwable error) {
    if (error instanceof TooManyRequestsBackoffRequiredException tooManyRequests) {
      return tooManyRequests.getRetryAfterSeconds();
    }

    Throwable current = error.getCause();
    while (current != null) {
      if (current instanceof TooManyRequestsBackoffRequiredException tooManyRequests) {
        return tooManyRequests.getRetryAfterSeconds();
      }
      current = current.getCause();
    }

    return null;
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    if (value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
  }
}
