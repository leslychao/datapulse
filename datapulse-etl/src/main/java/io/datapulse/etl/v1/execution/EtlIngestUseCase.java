package io.datapulse.etl.v1.execution;

import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestCommand;
import io.datapulse.etl.v1.flow.core.EtlSnapshotIngestionFlowConfig.IngestRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EtlIngestUseCase {

  private final ApplicationEventPublisher eventPublisher;

  public void ingest(IngestCommand command) {
    eventPublisher.publishEvent(new IngestRequested(this, command));
  }
}
