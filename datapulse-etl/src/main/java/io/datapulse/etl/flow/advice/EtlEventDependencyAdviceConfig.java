package io.datapulse.etl.flow.advice;

import io.datapulse.etl.exception.EtlEventDependencyNotSatisfiedException;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.service.EtlEventDependencyPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlEventDependencyAdviceConfig {

  private final EtlEventDependencyPolicy dependencyPolicy;

  @Bean
  public Advice etlEventDependencyAdvice() {
    return new EtlAbstractRequestHandlerAdvice() {

      @Override
      protected Object doInvoke(
          ExecutionCallback callback,
          Object target,
          Message<?> message
      ) {
        Object payload = message.getPayload();

        if (payload instanceof EtlSourceExecution execution) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Invoking ETL dependency check: requestId={}, accountId={}, marketplace={}, event={}, sourceId={}",
                execution.requestId(),
                execution.accountId(),
                execution.marketplace(),
                execution.event(),
                execution.sourceId()
            );
          }

          try {
            dependencyPolicy.assertDependenciesSatisfiedOrRetry(execution);
          } catch (EtlEventDependencyNotSatisfiedException ex) {
            log.warn(
                "ETL event dependencies not satisfied, failing execution: requestId={}, accountId={}, marketplace={}, event={}, sourceId={}, reason={}",
                execution.requestId(),
                execution.accountId(),
                execution.marketplace(),
                execution.event(),
                execution.sourceId(),
                ex.getMessage()
            );
            throw ex;
          }
        } else if (log.isDebugEnabled()) {
          log.debug(
              "Skipping ETL dependency check for unexpected payload type: {}",
              payload.getClass().getName()
          );
        }

        return callback.execute();
      }
    };
  }
}
