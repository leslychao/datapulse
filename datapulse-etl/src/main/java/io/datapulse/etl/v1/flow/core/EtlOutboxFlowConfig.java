package io.datapulse.etl.v1.flow.core;

import static io.datapulse.etl.v1.flow.core.EtlFlowConstants.CH_ETL_OUTBOX_PUBLISH;

import io.datapulse.etl.v1.execution.EtlExecutionOutboxRepository.OutboxRow;
import io.datapulse.etl.v1.execution.EtlOutboxPublisher;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

@Configuration
public class EtlOutboxFlowConfig {

  @Bean(name = CH_ETL_OUTBOX_PUBLISH)
  public MessageChannel etlOutboxPublishChannel() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow etlOutboxPublishFlow(EtlOutboxPublisher outboxPublisher) {
    return IntegrationFlow
        .from(CH_ETL_OUTBOX_PUBLISH)
        .split(List.class, rows -> rows)
        .handle(OutboxRow.class, (row, headers) -> {
          outboxPublisher.publishRow(row);
          return null;
        }, endpoint -> endpoint.requiresReply(false))
        .get();
  }
}
