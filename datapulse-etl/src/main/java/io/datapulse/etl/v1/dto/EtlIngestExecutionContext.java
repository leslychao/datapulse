package io.datapulse.etl.v1.dto;

import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;

public record EtlIngestExecutionContext(
    EtlSourceExecution execution,
    RegisteredSource registeredSource
) {

}
