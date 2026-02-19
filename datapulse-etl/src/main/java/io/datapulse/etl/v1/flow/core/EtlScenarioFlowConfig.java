package io.datapulse.etl.v1.flow.core;

import static io.datapulse.etl.v1.flow.core.EtlFlowConstants.CH_ETL_SCENARIO_RUN;
import static io.datapulse.etl.v1.flow.core.EtlFlowConstants.CH_ETL_SCENARIO_STEPS;
import static io.datapulse.etl.v1.flow.core.EtlFlowConstants.HDR_ETL_SCENARIO_REQUEST_ID;

import io.datapulse.etl.v1.dto.scenario.EtlScenarioRunRequest;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioRunResponse;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioStep;
import io.datapulse.etl.v1.flow.scenario.EtlScenarioPlanner;
import io.datapulse.etl.v1.flow.scenario.EtlScenarioStepExecutor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;

@Configuration
@RequiredArgsConstructor
public class EtlScenarioFlowConfig {

  private final EtlScenarioPlanner scenarioPlanner;
  private final EtlScenarioStepExecutor stepExecutor;

  @Bean(name = CH_ETL_SCENARIO_RUN)
  public MessageChannel etlScenarioRunChannel(
      @Qualifier("etlOrchestrateExecutor") TaskExecutor etlOrchestrateExecutor
  ) {
    return new ExecutorChannel(etlOrchestrateExecutor);
  }

  @Bean(name = CH_ETL_SCENARIO_STEPS)
  public MessageChannel etlScenarioStepsChannel() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow etlScenarioHttpInboundFlow() {
    return IntegrationFlow
        .from(
            Http.inboundGateway("/api/etl/scenario/run")
                .requestPayloadType(EtlScenarioRunRequest.class)
                .statusCodeFunction(message -> HttpStatus.ACCEPTED)
        )
        .enrichHeaders(headers -> headers
            .headerFunction(
                HDR_ETL_SCENARIO_REQUEST_ID,
                message -> UUID.randomUUID().toString()
            )
        )
        .wireTap(CH_ETL_SCENARIO_RUN)
        .handle((payload, headers) -> new EtlScenarioRunResponse(
            headers.get(HDR_ETL_SCENARIO_REQUEST_ID, String.class)
        ))
        .get();
  }

  @Bean
  public IntegrationFlow etlScenarioPlanFlow() {
    return IntegrationFlow
        .from(CH_ETL_SCENARIO_RUN)
        .handle(
            EtlScenarioRunRequest.class,
            (request, headers) -> scenarioPlanner.buildSteps(
                request,
                headers.get(HDR_ETL_SCENARIO_REQUEST_ID, String.class)
            )
        )
        .split()
        .channel(CH_ETL_SCENARIO_STEPS)
        .get();
  }

  @Bean
  public IntegrationFlow etlScenarioStepExecutionFlow() {
    return IntegrationFlow
        .from(CH_ETL_SCENARIO_STEPS)
        .handle(
            EtlScenarioStep.class,
            (step, headers) -> {
              stepExecutor.execute(step);
              return null;
            },
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }
}
