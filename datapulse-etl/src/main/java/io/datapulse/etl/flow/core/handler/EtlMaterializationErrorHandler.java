package io.datapulse.etl.flow.core.handler;

import io.datapulse.core.i18n.I18nMessageService;
import io.datapulse.etl.dto.ExecutionAggregationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlMaterializationErrorHandler {

  private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private final I18nMessageService i18nMessageService;

  public void handleMaterializationError(
      Throwable error,
      ExecutionAggregationResult aggregation
  ) {
    String requestId = aggregation.requestId();
    long accountId = aggregation.accountId();

    String errorClass = error.getClass().getName();
    String userErrorMessage = i18nMessageService.userMessage(error);
    String safeErrorMessage = truncate(userErrorMessage);

    log.error(
        "ETL materialization failed: requestId={}, accountId={}, event={}, errorClass={}, errorMessage={}",
        requestId,
        accountId,
        aggregation.event(),
        errorClass,
        safeErrorMessage,
        error
    );
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
