package io.datapulse.integration.api;

import io.datapulse.integration.domain.SyncHealth;

public record ConnectionSyncHealthResponse(
    long connectionId,
    String connectionName,
    String lastSuccessAt,
    SyncHealth status) {}
