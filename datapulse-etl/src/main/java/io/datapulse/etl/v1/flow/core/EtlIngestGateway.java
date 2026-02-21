package io.datapulse.etl.v1.flow.core;

import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestCommand;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;

@MessagingGateway
public interface EtlIngestGateway {

  @Gateway(requestChannel = EtlFlowConstants.CH_ETL_INGEST)
  void ingest(IngestCommand command);
}
