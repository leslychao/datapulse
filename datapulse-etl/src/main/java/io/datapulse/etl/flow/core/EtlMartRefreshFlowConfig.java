package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_MART_REFRESH_TRIGGER;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.ExecutionAggregationResult;
import io.datapulse.etl.service.MartRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlMartRefreshFlowConfig {

  private final MartRefreshService martRefreshService;

  @Bean
  public IntegrationFlow etlMartRefreshFlow(Advice etlMartRefreshExecutionAdvice) {
    return IntegrationFlow
        .from(CH_ETL_MART_REFRESH_TRIGGER)
        .handle(
            ExecutionAggregationResult.class,
            (aggregation, headers) -> {
              startMartRefresh(aggregation);
              return null;
            },
            endpoint -> endpoint
                .requiresReply(false)
                .advice(etlMartRefreshExecutionAdvice)
        )
        .get();
  }

  private void startMartRefresh(ExecutionAggregationResult aggregation) {
    MarketplaceEvent event = aggregation.event();

    log.info(
        "Starting mart refresh after ETL materialization: requestId={}, accountId={}, event={}, dateFrom={}, dateTo={}",
        aggregation.requestId(),
        aggregation.accountId(),
        event,
        aggregation.dateFrom(),
        aggregation.dateTo()
    );

    martRefreshService.refreshAfterEvent(
        aggregation.accountId(),
        event,
        aggregation.requestId()
    );
  }
}
