package io.datapulse.etl.v1.flow.core.handler;

import io.datapulse.core.i18n.I18nMessageService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.marketplaces.resilience.TooManyRequestsBackoffRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlIngestErrorHandler {

  private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private final I18nMessageService i18nMessageService;

  public ExecutionOutcome handleIngestError(
      Throwable error,
      EtlSourceExecution execution
  ) {
    String requestId = execution.requestId();
    long accountId = execution.accountId();
    String sourceId = execution.sourceId();
    MarketplaceType marketplace = execution.marketplace();
    MarketplaceEvent event = execution.event();

    IngestStatus status = determineStatus(error);

    String errorClass = error.getClass().getName();
    String userErrorMessage = i18nMessageService.userMessage(error);
    String safeErrorMessage = truncate(userErrorMessage);
    Long retryAfterMillis = resolveRetryAfterMillis(error);

    log.warn(
        "ETL ingest failed: requestId={}, accountId={}, event={}, marketplace={}, sourceId={}, "
            + "status={}, errorClass={}, errorMessage={}",
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
          "ETL ingest failed with full stacktrace: requestId={}, accountId={}, event={}, "
              + "marketplace={}, sourceId={}, status={}, errorClass={}",
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
        execution.dateFrom(),
        execution.dateTo(),
        status,
        0L,
        safeErrorMessage,
        retryAfterMillis
    );
  }

  private IngestStatus determineStatus(Throwable error) {
    return findTooManyRequests(error) != null
        ? IngestStatus.WAITING_RETRY
        : IngestStatus.FAILED;
  }

  private TooManyRequestsBackoffRequiredException findTooManyRequests(Throwable error) {
    if (error instanceof TooManyRequestsBackoffRequiredException tooManyRequests) {
      return tooManyRequests;
    }

    Throwable current = error.getCause();
    while (current != null) {
      if (current instanceof TooManyRequestsBackoffRequiredException tooManyRequests) {
        return tooManyRequests;
      }
      current = current.getCause();
    }

    return null;
  }

  private Long resolveRetryAfterMillis(Throwable error) {
    TooManyRequestsBackoffRequiredException tooManyRequests = findTooManyRequests(error);
    if (tooManyRequests == null) {
      return null;
    }
    int retryAfterSeconds = tooManyRequests.getRetryAfterSeconds();
    if (retryAfterSeconds <= 0) {
      return null;
    }
    return retryAfterSeconds * 1_000L;
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
