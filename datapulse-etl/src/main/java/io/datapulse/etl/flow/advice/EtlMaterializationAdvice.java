package io.datapulse.etl.flow.advice;

import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ERROR_MESSAGE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SYNC_STATUS;

import io.datapulse.domain.SyncStatus;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EtlMaterializationAdvice extends AbstractRequestHandlerAdvice {

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

      log.error(
          "ETL materialization failed: requestId={}, event={}, from={}, to={}",
          requestId,
          eventValue,
          from,
          to,
          ex
      );

      SyncStatus syncStatus = SyncStatus.ERROR;

      String existingError = headers.get(HDR_ETL_ERROR_MESSAGE, String.class);
      String materializationError = ex.getMessage();

      String combinedError;
      if (StringUtils.isNotBlank(existingError) && StringUtils.isNotBlank(materializationError)) {
        combinedError = existingError + "; materializationError=" + materializationError;
      } else if (StringUtils.isNotBlank(materializationError)) {
        combinedError = "materializationError=" + materializationError;
      } else if (StringUtils.isNotBlank(existingError)) {
        combinedError = existingError;
      } else {
        combinedError = "materialization failed without message";
      }

      return MessageBuilder
          .withPayload(message.getPayload())
          .copyHeaders(message.getHeaders())
          .setHeader(HDR_ETL_SYNC_STATUS, syncStatus)
          .setHeader(HDR_ETL_ERROR_MESSAGE, combinedError)
          .build();
    }
  }
}
