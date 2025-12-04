package io.datapulse.etl.flow.orchestrator;

import static io.datapulse.etl.flow.core.FlowChannels.CH_ORCHESTRATE;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;

@Configuration
public class WarehouseScheduleFlowConfiguration {

  private final ScheduledRunFactory scheduledRunFactory;
  private final OrchestrationCommandMapper commandMapper;

  public WarehouseScheduleFlowConfiguration(
      ScheduledRunFactory scheduledRunFactory,
      OrchestrationCommandMapper commandMapper
  ) {
    this.scheduledRunFactory = scheduledRunFactory;
    this.commandMapper = commandMapper;
  }

  @Bean
  public IntegrationFlow warehouseDailyFlow() {
    return IntegrationFlow.fromSupplier(
            () -> scheduledRunFactory.buildDailyRequests(MarketplaceEvent.WAREHOUSE_DICT),
            spec -> spec.poller(Pollers.cron("0 0 * * * *"))
        )
        .split()
        .transform(EtlRunRequest.class, commandMapper::fromRequest)
        .channel(CH_ORCHESTRATE)
        .get();
  }
}
