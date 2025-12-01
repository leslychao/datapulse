package io.datapulse.etl.flow.advice;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.core.i18n.I18nMessageService;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.dto.OrchestrationBundle;
import io.datapulse.etl.flow.OrchestrationCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EtlOrchestratorPlansAdvice extends EtlAbstractRequestHandlerAdvice {

  private final I18nMessageService i18nMessageService;

  @Override
  protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
    try {
      return callback.execute();
    } catch (Exception ex) {
      Object payload = message.getPayload();

      if (payload instanceof OrchestrationCommand command) {
        log.warn(
            "ETL orchestration failed while building marketplace plans: requestId={}, accountId={}, event={}, cause={}",
            command.requestId(),
            command.accountId(),
            command.event(),
            ex.getMessage()
        );

        String errorMessage = i18nMessageService.userMessage(unwrapProcessingError(ex));

        return new OrchestrationBundle(
            command.requestId(),
            command.accountId(),
            command.event(),
            command.from(),
            command.to(),
            SyncStatus.ERROR,
            "orchestrator",
            errorMessage,
            List.of(),
            null
        );
      }
      throw new AppException(ETL_REQUEST_INVALID, "Unexpected payload type in plans advice");
    }
  }
}
