package io.datapulse.etl.handler.error;

import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;

import io.datapulse.etl.dto.IngestResult;
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
  private static final String UNKNOWN_SOURCE_ID = "UNKNOWN_SOURCE";

  public IngestResult handleIngestError(
      Throwable error,
      Message<?> message
  ) {
    MessageHeaders headers = message.getHeaders();

    String sourceId = resolveSourceId(headers);
    IngestStatus status = determineStatus(error);

    String errorClass = error.getClass().getName();
    String rawErrorMessage = resolveErrorMessage(error);
    String safeErrorMessage = truncate(rawErrorMessage, MAX_ERROR_MESSAGE_LENGTH);

    log.warn(
        "ETL ingest failed: sourceId={}, status={}, errorClass={}, errorMessage={}",
        sourceId,
        status,
        errorClass,
        safeErrorMessage
    );
    if (log.isDebugEnabled()) {
      log.debug(
          "ETL ingest failed with full stacktrace: sourceId={}, status={}, errorClass={}",
          sourceId,
          status,
          errorClass,
          error
      );
    }

    return new IngestResult(
        sourceId,
        status,
        errorClass,
        safeErrorMessage
    );
  }

  private String resolveSourceId(MessageHeaders headers) {
    String headerValue = headers.get(HDR_ETL_SOURCE_ID, String.class);
    if (headerValue != null && !headerValue.isBlank()) {
      return headerValue;
    }
    return UNKNOWN_SOURCE_ID;
  }

  private IngestStatus determineStatus(Throwable error) {
    return isBackoffRequired(error) ? IngestStatus.WAIT : IngestStatus.ERROR;
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

  private String resolveErrorMessage(Throwable error) {
    String message = error.getMessage();
    if (message != null && !message.isBlank()) {
      return message;
    }
    return error.toString();
  }

  private String truncate(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
