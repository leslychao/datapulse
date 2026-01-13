package io.datapulse.etl.flow.advice;

import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.flow.core.handler.EtlMartRefreshErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlMartRefreshExecutionAdviceConfig {

  private final EtlMartRefreshErrorHandler martRefreshErrorHandler;

  @Bean
  public Advice etlMartRefreshExecutionAdvice() {
    return new EtlAbstractRequestHandlerAdvice() {

      @Override
      protected Object doInvoke(
          ExecutionCallback callback,
          Object target,
          Message<?> message
      ) {
        Object payload = message.getPayload();
        if (!(payload instanceof ExecutionAggregationResult aggregation)) {
          throw new IllegalStateException(
              "ETL mart refresh advice expects ExecutionAggregationResult payload, but got: "
                  + payload.getClass().getName()
          );
        }

        try {
          return callback.execute();
        } catch (Exception ex) {
          Throwable cause = unwrapProcessingError(ex);
          martRefreshErrorHandler.handleMartRefreshError(cause, aggregation);
          return null;
        }
      }
    };
  }
}
