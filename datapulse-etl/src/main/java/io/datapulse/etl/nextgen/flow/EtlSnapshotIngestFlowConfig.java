package io.datapulse.etl.nextgen.flow;

import static io.datapulse.etl.nextgen.constants.NextGenEtlChannels.CH_INGEST;

import io.datapulse.etl.nextgen.dto.ExecutionDispatch;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.MessageChannel;

@Configuration
public class EtlSnapshotIngestFlowConfig {

  @Bean(name = "ngSnapshotIngress")
  public MessageChannel snapshotIngressChannel() {
    return new DirectChannel();
  }

  @Bean(name = "ngSnapshotToIngestBridge")
  public MessageChannel snapshotToIngestBridge() {
    return new DirectChannel();
  }

  @Bean
  public IntegrationFlow snapshotIngressFlow() {
    return IntegrationFlows
        .from("ngSnapshotIngress")
        .transform(ExecutionDispatch.class, dispatch -> dispatch)
        .channel(CH_INGEST)
        .get();
  }
}
