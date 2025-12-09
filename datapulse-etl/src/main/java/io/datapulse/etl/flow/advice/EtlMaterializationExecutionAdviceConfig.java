package io.datapulse.etl.flow.advice;

import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.flow.core.handler.EtlMaterializationErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlMaterializationExecutionAdviceConfig {

  private final EtlMaterializationErrorHandler materializationErrorHandler;

  @Bean
  public Advice etlMaterializationExecutionAdvice() {
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
              "ETL materialization advice expects ExecutionAggregationResult payload, but got: "
                  + payload.getClass().getName()
          );
        }

        try {
          return callback.execute();
        } catch (Exception ex) {
          Throwable cause = unwrapProcessingError(ex);
          materializationErrorHandler.handleMaterializationError(cause, aggregation);
          return null;
        }
      }
    };
  }
}
