package io.datapulse.etl.flow;

import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.flow.core.EtlOrchestrationCommandFactory;
import io.datapulse.etl.flow.core.EtlScheduledRunRequestFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;

@Configuration
@RequiredArgsConstructor
public class EtlWarehouseScheduledFlowConfig {

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final EtlScheduledRunRequestFactory etlScheduledRunRequestFactory;
  private final EtlOrchestrationCommandFactory orchestrationCommandFactory;

  @Bean
  public IntegrationFlow etlWarehouseScheduledRunFlow() {
    return IntegrationFlow
        .fromSupplier(
            () -> etlScheduledRunRequestFactory.buildDailyRunRequests(MarketplaceEvent.WAREHOUSE),
            spec -> spec.poller(Pollers.cron("0 0 * * * *"))
        )
        .split()
        .transform(EtlRunRequest.class, orchestrationCommandFactory::toCommand)
        .handle(
            Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                .exchangeName(EXCHANGE_EXECUTION)
                .routingKey(ROUTING_KEY_EXECUTION)
        )
        .get();
  }
}
