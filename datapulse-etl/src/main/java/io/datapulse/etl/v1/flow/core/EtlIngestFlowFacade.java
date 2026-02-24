package io.datapulse.etl.v1.flow.core;

import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlIngestFlowFacade {

  @Qualifier(EtlFlowConstants.CH_ETL_INGEST)
  private final MessageChannel etlIngestChannel;

  public void ingest(IngestCommand command) {
    etlIngestChannel.send(new GenericMessage<>(command));
  }
}
