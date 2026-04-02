package io.datapulse.etl.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.integration.domain.ManualSyncEligibilityChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobManualSyncEligibilityChecker implements ManualSyncEligibilityChecker {

    private final JobExecutionRepository jobExecutionRepository;

    @Override
    public void ensureCanTriggerManualSync(long connectionId) {
        if (jobExecutionRepository.existsActiveForConnection(connectionId)) {
            throw ConflictException.of(MessageCodes.JOB_ACTIVE_EXISTS, connectionId);
        }
    }
}
