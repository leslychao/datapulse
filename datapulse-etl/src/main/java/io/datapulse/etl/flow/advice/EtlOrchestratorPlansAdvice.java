package io.datapulse.etl.flow.advice;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.flow.EtlOrchestratorFlowConfig.OrchestrationBundle;
import io.datapulse.etl.flow.EtlOrchestratorFlowConfig.OrchestrationCommand;
import io.datapulse.etl.i18n.ExceptionMessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EtlOrchestratorPlansAdvice extends AbstractRequestHandlerAdvice {

  private final ExceptionMessageService exceptionMessageService;

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

        String errorMessage = exceptionMessageService.userMessage(unwrapProcessingError(ex));

        return new OrchestrationBundle(
            command.requestId(),
            command.accountId(),
            command.event(),
            command.from(),
            command.to(),
            SyncStatus.ERROR,
            "orchestrator",
            errorMessage,
            List.of()
        );
      }
      throw new AppException(ETL_REQUEST_INVALID, "Unexpected payload type in plans advice");
    }
  }
  private Throwable unwrapProcessingError(Throwable error) {
    Throwable current = error;

    while (true) {
      if (current instanceof ThrowableHolderException holder && holder.getCause() != null) {
        current = holder.getCause();
        continue;
      }
      if (current instanceof MessageHandlingException mhe && mhe.getCause() != null) {
        current = mhe.getCause();
        continue;
      }
      if (current instanceof MessagingException me && me.getCause() != null) {
        current = me.getCause();
        continue;
      }
      return current;
    }
  }
}
