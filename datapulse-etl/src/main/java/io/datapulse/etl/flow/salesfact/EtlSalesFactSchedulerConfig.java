package io.datapulse.etl.flow.salesfact;

import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ORCHESTRATE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;

import io.datapulse.core.service.AccountService;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.flow.EtlOrchestratorFlowConfig.EtlRunRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;

@Configuration
@RequiredArgsConstructor
public class EtlSalesFactSchedulerConfig {

  private final AccountService accountService;

  @Bean
  public IntegrationFlow scheduledSalesFactEtlFlow(
      @Value("${etl.sales-fact.cron:0 10 3 * * *}") String salesFactCron
  ) {
    return IntegrationFlow
        .fromSupplier(
            this::buildDailySalesFactRunRequests,
            sourceSpec -> sourceSpec
                .id("etlSalesFactCronAdapter")
                .autoStartup(true)
                .poller(Pollers.cron(salesFactCron))
        )
        .split()
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_ETL_REQUEST_ID,
            message -> UUID.randomUUID().toString()
        ))
        .channel(CH_ETL_ORCHESTRATE)
        .get();
  }

  private List<EtlRunRequest> buildDailySalesFactRunRequests() {
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(1);

    List<Long> activeAccountIds = accountService.getActiveIds();
    if (activeAccountIds.isEmpty()) {
      return List.of();
    }

    return activeAccountIds.stream()
        .map(accountId -> new EtlRunRequest(
            accountId,
            MarketplaceEvent.SALES_FACT.name(),
            from,
            to
        ))
        .toList();
  }
}
