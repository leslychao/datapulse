package io.datapulse.etl.v1.flow;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.v1.dto.EtlRunRequest;
import io.datapulse.etl.v1.flow.core.EtlOrchestrationCommandFactory;
import io.datapulse.etl.v1.flow.core.EtlScheduledRunRequestFactory;
import io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants;
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
            () -> etlScheduledRunRequestFactory.buildDailyRunRequests(
                MarketplaceEvent.WAREHOUSE_DICT),
            spec -> spec.poller(Pollers.cron("0 0 * * * *"))
        )
        .split()
        .transform(
            EtlRunRequest.class,
            request -> orchestrationCommandFactory.toCommand(
                null,
                request.accountId(),
                request.event(),
                request.dateMode(),
                request.dateFrom(),
                request.dateTo(),
                request.lastDays(),
                request.sourceIds()
            )
        )
        .handle(
            Amqp.outboundAdapter(etlExecutionRabbitTemplate)
                .exchangeName(EtlExecutionAmqpConstants.EXCHANGE_EXECUTION)
                .routingKey(EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION)
        )
        .get();
  }
}
