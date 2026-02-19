package io.datapulse.etl.v1.flow.advice;

import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;

public abstract class EtlAbstractRequestHandlerAdvice extends AbstractRequestHandlerAdvice {

  protected Throwable unwrapProcessingError(Throwable error) {
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
