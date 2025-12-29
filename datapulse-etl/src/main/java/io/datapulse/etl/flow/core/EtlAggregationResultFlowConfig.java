package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;

import io.datapulse.core.service.EtlExecutionAuditService;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.service.EtlMaterializationService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlAggregationResultFlowConfig {

  private final EtlMaterializationService etlMaterializationService;
  private final EtlExecutionAuditService etlExecutionAuditService;

  @Bean(name = CH_ETL_ORCHESTRATION_RESULT)
  public MessageChannel etlOrchestrationResultChannel(
      @Qualifier("etlOrchestrateExecutor") TaskExecutor etlOrchestrateExecutor
  ) {
    return new PublishSubscribeChannel(etlOrchestrateExecutor);
  }

  @Bean
  public IntegrationFlow etlExecutionAuditFlow(Advice etlExecutionAuditAdvice) {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .transform(ExecutionAggregationResult.class, this::buildExecutionAuditDtos)
        .split()
        .handle(
            EtlExecutionAuditDto.class,
            (dto, headers) -> {
              etlExecutionAuditService.save(dto);
              log.info(
                  "ETL execution audit saved: requestId={}, accountId={}, event={}, marketplace={}, sourceId={}, status={}, rowsCount={}",
                  dto.getRequestId(),
                  dto.getAccountId(),
                  dto.getEvent(),
                  dto.getMarketplace(),
                  dto.getSourceId(),
                  dto.getStatus(),
                  dto.getRowsCount()
              );
              return null;
            },
            endpoint -> endpoint
                .requiresReply(false)
                .advice(etlExecutionAuditAdvice)
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
            spec -> spec.discardFlow(df -> df.handle((payload, headers) -> null))
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

  private List<EtlExecutionAuditDto> buildExecutionAuditDtos(
      ExecutionAggregationResult aggregation
  ) {
    Map<String, List<ExecutionOutcome>> bySource = aggregation.outcomes().stream()
        .filter(outcome -> outcome.sourceId() != null)
        .collect(Collectors.groupingBy(ExecutionOutcome::sourceId));

    return bySource.entrySet().stream()
        .map(entry -> toAuditDto(aggregation, entry.getKey(), entry.getValue()))
        .toList();
  }

  private EtlExecutionAuditDto toAuditDto(
      ExecutionAggregationResult aggregation,
      String sourceId,
      List<ExecutionOutcome> outcomes
  ) {
    List<ExecutionOutcome> terminalOutcomes = outcomes.stream()
        .filter(o -> o.status().isTerminal())
        .toList();

    if (terminalOutcomes.isEmpty()) {
      throw new IllegalStateException(
          "No terminal ingest outcomes found for sourceId=" + sourceId
              + ", requestId=" + aggregation.requestId()
      );
    }

    ExecutionOutcome finalOutcome = terminalOutcomes.get(terminalOutcomes.size() - 1);
    SyncStatus syncStatus = mapFinalIngestStatus(finalOutcome.status());

    EtlExecutionAuditDto dto = new EtlExecutionAuditDto();
    dto.setRequestId(aggregation.requestId());
    dto.setRawSyncId(finalOutcome.rawSyncId());
    dto.setAccountId(aggregation.accountId());
    dto.setEvent(aggregation.event().name());
    dto.setMarketplace(finalOutcome.marketplace());
    dto.setSourceId(sourceId);
    dto.setDateFrom(aggregation.dateFrom());
    dto.setDateTo(aggregation.dateTo());
    dto.setStatus(syncStatus);
    dto.setRowsCount(finalOutcome.rowsCount());
    dto.setErrorMessage(finalOutcome.errorMessage());
    return dto;
  }

  private SyncStatus mapFinalIngestStatus(IngestStatus ingestStatus) {
    return switch (ingestStatus) {
      case SUCCESS -> SyncStatus.SUCCESS;
      case NO_DATA -> SyncStatus.NO_DATA;
      case FAILED -> SyncStatus.FAILED;
      default -> throw new IllegalStateException(
          "Unexpected terminal ingest status for audit: " + ingestStatus
      );
    };
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
        aggregation.rawSyncId()
    );
  }
}
