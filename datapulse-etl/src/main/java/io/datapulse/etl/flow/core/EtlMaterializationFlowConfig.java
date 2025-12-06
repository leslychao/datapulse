package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_ORCHESTRATION_RESULT;

import io.datapulse.domain.SyncStatus;
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
            OrchestrationBundle.class,
            bundle -> bundle.syncStatus() == SyncStatus.SUCCESS
        )
        .handle(
            OrchestrationBundle.class,
            (bundle, headers) -> {
              log.info(
                  "Materialization: requestId={}, event={}, from={}, to={}",
                  bundle.requestId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo()
              );

              materializationService.materialize(
                  bundle.accountId(),
                  bundle.event(),
                  bundle.dateFrom(),
                  bundle.dateTo(),
                  bundle.requestId()
              );
              return null;
            },
            endpoint -> endpoint
                .requiresReply(false)
                .advice(etlMaterializationAdvice)
        )
        .log(
            LoggingHandler.Level.INFO,
            m -> "Materialization completed for requestId=" +
                ((OrchestrationBundle) m.getPayload()).requestId()
        )
        .nullChannel();
  }
}
