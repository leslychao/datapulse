package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_PREPARE_RAW_SCHEMA;

import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.OrchestrationPlan;
import io.datapulse.etl.repository.RawTableSchemaRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlRawSchemaPreparationFlowConfig {

  private final RawTableSchemaRepository rawTableSchemaRepository;

  @Bean
  public IntegrationFlow etlPrepareRawSchemaFlow() {
    return IntegrationFlow
        .from(CH_ETL_PREPARE_RAW_SCHEMA)
        .handle(
            OrchestrationPlan.class,
            (plan, headers) -> {
              Set<String> rawTables = plan.plans().stream()
                  .flatMap(marketplacePlan -> marketplacePlan.executions().stream())
                  .map(EtlSourceExecution::rawTable)
                  .collect(Collectors.toCollection(LinkedHashSet::new));

              rawTables.forEach(rawTableSchemaRepository::ensureTableExists);

              log.info(
                  "ETL raw schema prepared: tablesCount={}, expectedExecutions={}",
                  rawTables.size(),
                  plan.expectedExecutions()
              );

              return plan;
            }
        )
        .get();
  }
}
