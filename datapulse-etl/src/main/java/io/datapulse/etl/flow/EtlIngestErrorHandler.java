package io.datapulse.etl.flow;

import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;

import io.datapulse.etl.flow.dto.IngestResult;
import io.datapulse.etl.i18n.ExceptionMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class EtlIngestErrorHandler {

  private final ExceptionMessageService exceptionMessageService;

  public IngestResult handleIngestError(
      Throwable throwable,
      Message<?> message
  ) {
    MessageHeaders headers = message.getHeaders();
    String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);

    exceptionMessageService.logEtlError(throwable);

    String errorClass = throwable != null ? throwable.getClass().getSimpleName() : null;
    String errorMessage = throwable != null ? throwable.getMessage() : null;

    return new IngestResult(
        sourceId,
        false,
        errorClass,
        errorMessage
    );
  }
}
