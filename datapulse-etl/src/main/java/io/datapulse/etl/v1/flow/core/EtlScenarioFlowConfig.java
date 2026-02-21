package io.datapulse.etl.v1.flow.core;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_TASKS;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_TASKS;

import io.datapulse.etl.v1.dto.scenario.EtlScenarioRunRequest;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioRunResponse;
import io.datapulse.etl.v1.flow.scenario.EtlScenarioPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;

@Configuration
@RequiredArgsConstructor
public class EtlScenarioFlowConfig {

  private final EtlScenarioPlanner scenarioPlanner;
  private final RabbitTemplate rabbitTemplate;

  @Bean
  public IntegrationFlow etlScenarioHttpInboundFlow() {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/scenario/run")
            .requestPayloadType(EtlScenarioRunRequest.class)
            .statusCodeFunction(message -> HttpStatus.ACCEPTED))
        .transform(EtlScenarioRunRequest.class, scenarioPlanner::buildRunTasks)
        .split()
        .handle(Amqp.outboundAdapter(rabbitTemplate).exchangeName(EXCHANGE_TASKS).routingKey(ROUTING_KEY_TASKS),
            endpoint -> endpoint.requiresReply(false))
        .handle(payload -> new EtlScenarioRunResponse("accepted"))
        .get();
  }
}
