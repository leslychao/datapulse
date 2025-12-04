package io.datapulse.etl.flow.materialization;

import static io.datapulse.etl.flow.core.FlowChannels.CH_EXECUTION_RESULT;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_EVENT_AGGREGATION;

import io.datapulse.etl.flow.core.model.EventAggregation;
import io.datapulse.etl.flow.core.policy.MaterializationPolicy;
import io.datapulse.etl.service.EtlMaterializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

@Configuration
public class MaterializationFlowConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(MaterializationFlowConfiguration.class);

  private final EtlMaterializationService materializationService;
  private final MaterializationPolicy materializationPolicy;

  public MaterializationFlowConfiguration(
      EtlMaterializationService materializationService,
      MaterializationPolicy materializationPolicy
  ) {
    this.materializationService = materializationService;
    this.materializationPolicy = materializationPolicy;
  }

  @Bean
  public IntegrationFlow materializationFlow() {
    return IntegrationFlow.from(CH_EXECUTION_RESULT)
        .filter(message -> message.getHeaders().get(HDR_EVENT_AGGREGATION) != null)
        .filter(message -> {
          EventAggregation aggregation = message.getHeaders().get(HDR_EVENT_AGGREGATION, EventAggregation.class);
          return aggregation != null && materializationPolicy.readyForMaterialization(aggregation);
        })
        .handle((payload, headers) -> {
          EventAggregation aggregation = headers.get(HDR_EVENT_AGGREGATION, EventAggregation.class);
          if (aggregation != null) {
            LOG.info(
                "Materialization triggered: requestId={}, status={}, hasData={}",
                aggregation.requestId(),
                aggregation.status(),
                aggregation.hasData()
            );
            materializationService.materialize(
                aggregation.accountId(),
                aggregation.event(),
                aggregation.from(),
                aggregation.to(),
                aggregation.requestId()
            );
          }
          return aggregation;
        })
        .get();
  }
}
