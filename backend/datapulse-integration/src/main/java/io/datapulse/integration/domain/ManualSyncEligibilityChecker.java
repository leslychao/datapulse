package io.datapulse.integration.domain;

/**
 * Optional guard supplied by ETL when the application includes {@code datapulse-etl}. Ensures manual
 * sync is not enqueued while a {@code job_execution} is already active for the connection.
 */
public interface ManualSyncEligibilityChecker {

    void ensureCanTriggerManualSync(long connectionId);
}
