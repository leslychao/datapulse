package io.datapulse.etl.v1.flow.advice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlExecutionAuditAdviceConfig {

  @Bean
  public Advice etlExecutionAuditAdvice() {
    return new EtlAbstractRequestHandlerAdvice() {

      @Override
      protected Object doInvoke(
          ExecutionCallback callback,
          Object target,
          Message<?> message
      ) {
        try {
          return callback.execute();
        } catch (Exception ex) {
          Throwable cause = unwrapProcessingError(ex);
          log.error("Failed to persist ETL execution audit: {}", cause.getMessage(), cause);
          return null;
        }
      }
    };
  }
}
