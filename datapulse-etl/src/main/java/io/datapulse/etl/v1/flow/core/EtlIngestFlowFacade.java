package io.datapulse.etl.v1.flow.core;

import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestCommand;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;

@Component
public class EtlIngestFlowFacade {

  private final MessageChannel etlIngestChannel;

  public EtlIngestFlowFacade(
      @Qualifier(EtlFlowConstants.CH_ETL_INGEST) MessageChannel etlIngestChannel
  ) {
    this.etlIngestChannel = etlIngestChannel;
  }

  public void ingest(IngestCommand command) {
    etlIngestChannel.send(new GenericMessage<>(command));
  }
}
