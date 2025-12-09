package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;

import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.flow.advice.EtlAbstractRequestHandlerAdvice;
import io.datapulse.etl.flow.core.handler.EtlMaterializationErrorHandler;
import io.datapulse.etl.service.EtlMaterializationService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlAggregationResultFlowConfig {

  private final EtlMaterializationService etlMaterializationService;
  private final EtlMaterializationErrorHandler materializationErrorHandler;

  @Bean(name = CH_ETL_ORCHESTRATION_RESULT)
  public MessageChannel etlOrchestrationResultChannel(
      @Qualifier("etlOrchestrateExecutor") TaskExecutor etlOrchestrateExecutor
  ) {
    return new PublishSubscribeChannel(etlOrchestrateExecutor);
  }

  @Bean
  public Advice etlMaterializationExecutionAdvice() {
    return new EtlAbstractRequestHandlerAdvice() {

      @Override
      protected Object doInvoke(
          ExecutionCallback callback,
          Object target,
          Message<?> message
      ) {
        ExecutionAggregationResult aggregation =
            (ExecutionAggregationResult) message.getPayload();

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

  @Bean
  public IntegrationFlow etlAggregationAuditFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .handle(
            ExecutionAggregationResult.class,
            (aggregation, headers) -> {
              onIngestionCompletedForAudit(aggregation);
              return null;
            },
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  @Bean
  public IntegrationFlow etlAggregationMaterializationFlow(
      Advice etlMaterializationExecutionAdvice
  ) {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .filter(
            ExecutionAggregationResult.class,
            this::hasSuccessfulOutcome,
            spec -> spec.discardFlow(df -> df
                .handle((payload, headers) -> null)
            )
        )
        .handle(
            ExecutionAggregationResult.class,
            (aggregation, headers) -> {
              startMaterialization(aggregation);
              return null;
            },
            endpoint -> endpoint
                .requiresReply(false)
                .advice(etlMaterializationExecutionAdvice)
        )
        .get();
  }

  private void onIngestionCompletedForAudit(
      ExecutionAggregationResult aggregation
  ) {
    List<ExecutionOutcome> outcomes = aggregation.outcomes();

    List<String> failedSourceIds = outcomes.stream()
        .filter(o -> o.status() == IngestStatus.FAILED || o.status() == IngestStatus.WAITING_RETRY)
        .map(ExecutionOutcome::sourceId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    String failedSourcesSummary = failedSourceIds.isEmpty()
        ? null
        : String.join(",", failedSourceIds);

    // TODO: заменить на реальный вызов etlSyncAuditService, пока только лог
    log.info(
        "ETL aggregation audit: requestId={}, accountId={}, event={}, totalOutcomes={}, failedSources={}",
        aggregation.requestId(),
        aggregation.accountId(),
        aggregation.event(),
        outcomes.size(),
        failedSourcesSummary
    );
  }

  private boolean hasSuccessfulOutcome(ExecutionAggregationResult aggregation) {
    return aggregation.outcomes()
        .stream()
        .anyMatch(outcome -> outcome.status() == IngestStatus.SUCCESS);
  }

  private void startMaterialization(ExecutionAggregationResult aggregation) {
    etlMaterializationService.materialize(
        aggregation.accountId(),
        aggregation.event(),
        aggregation.dateFrom(),
        aggregation.dateTo(),
        aggregation.requestId()
    );
  }
}
