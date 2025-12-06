package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;

import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.flow.advice.EtlMaterializationAdvice;
import io.datapulse.etl.service.EtlMaterializationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlMaterializationFlowConfig {

  private final EtlMaterializationService materializationService;

  private final EtlMaterializationAdvice etlMaterializationAdvice;

  @Bean
  public IntegrationFlow etlMaterializationFlow() {
    return IntegrationFlow
        .from(CH_ETL_ORCHESTRATION_RESULT)
        .filter(
            ExecutionAggregationResult.class,
            bundle -> bundle.outcomes().stream().anyMatch(
                executionOutcome -> executionOutcome.status() == IngestStatus.SUCCESS)
        )
        .handle(
            ExecutionAggregationResult.class,
            (bundle, headers) -> {
              log.info(
                  "Materialization: requestId={}, event={}, from={}, to={}",
                  headers.get(HDR_ETL_REQUEST_ID, String.class),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo()
              );

              materializationService.materialize(
                  bundle.accountId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo(),
                  headers.get(HDR_ETL_REQUEST_ID, String.class)
              );
              return null;
            },
            endpoint -> endpoint
                .requiresReply(false)
                .advice(etlMaterializationAdvice)
        )
        .log(
            LoggingHandler.Level.INFO,
            m -> "Materialization completed for requestId=" + m.getHeaders()
                .get(HDR_ETL_REQUEST_ID, String.class)
        )
        .nullChannel();
  }
}
