package io.datapulse.etl.flow.advice;

import static io.datapulse.domain.MessageCodes.ETL_MATERIALIZATION_FALLBACK_ERROR;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ERROR_MESSAGE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SYNC_STATUS;

import io.datapulse.domain.SyncStatus;
import io.datapulse.etl.i18n.ExceptionMessageService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EtlMaterializationAdvice extends AbstractRequestHandlerAdvice {

  private final ExceptionMessageService exceptionMessageService;

  @Override
  protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
    try {
      return callback.execute();
    } catch (Exception ex) {
      MessageHeaders headers = message.getHeaders();

      String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
      String eventValue = headers.get(HDR_ETL_EVENT, String.class);
      LocalDate from = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
      LocalDate to = headers.get(HDR_ETL_DATE_TO, LocalDate.class);

      String logMessage = exceptionMessageService.userMessage(ex);
      log.error(
          "ETL materialization failed: {} requestId={}, event={}, from={}, to={}",
          logMessage,
          requestId,
          eventValue,
          from,
          to,
          ex
      );

      String existingError = headers.get(HDR_ETL_ERROR_MESSAGE, String.class);
      String materializationError = exceptionMessageService.userMessage(ex);

      String combinedError = combineMessages(existingError, materializationError);

      return MessageBuilder
          .withPayload(message.getPayload())
          .copyHeaders(message.getHeaders())
          .setHeader(HDR_ETL_SYNC_STATUS, SyncStatus.ERROR)
          .setHeader(HDR_ETL_ERROR_MESSAGE, combinedError)
          .build();
    }
  }

  private String combineMessages(String existingError, String materializationError) {
    boolean hasExisting = hasText(existingError);
    boolean hasMaterialization = hasText(materializationError);

    if (hasExisting && hasMaterialization) {
      return existingError + "; " + materializationError;
    }
    if (hasMaterialization) {
      return materializationError;
    }
    if (hasExisting) {
      return existingError;
    }
    return exceptionMessageService.userMessage(ETL_MATERIALIZATION_FALLBACK_ERROR);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
