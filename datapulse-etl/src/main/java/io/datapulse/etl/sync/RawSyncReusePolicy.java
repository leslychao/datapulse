package io.datapulse.etl.sync;

import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionOutcome;

public interface RawSyncReusePolicy {

  boolean requiresSync(EtlSourceExecution execution);

  ExecutionOutcome reuseFromAudit(EtlSourceExecution execution);
}
