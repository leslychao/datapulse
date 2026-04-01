package io.datapulse.execution.persistence;

import io.datapulse.execution.domain.ActionReconciliationSource;
import io.datapulse.execution.domain.ActionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * JDBC-based CAS (Compare-And-Swap) operations on price_action.
 * Each method returns the number of rows updated (0 = CAS conflict, 1 = success).
 */
@Repository
@RequiredArgsConstructor
public class PriceActionCasRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String CAS_STATUS = """
            UPDATE price_action
            SET status = :newStatus, updated_at = now()
            WHERE id = :id AND status = :expectedStatus
            """;

    private static final String CAS_APPROVE = """
            UPDATE price_action
            SET status = 'APPROVED',
                approved_by_user_id = :userId,
                approved_at = now(),
                updated_at = now()
            WHERE id = :id AND status = :expectedStatus
            """;

    private static final String CAS_HOLD = """
            UPDATE price_action
            SET status = 'ON_HOLD',
                hold_reason = :holdReason,
                updated_at = now()
            WHERE id = :id AND status = 'APPROVED'
            """;

    private static final String CAS_CANCEL = """
            UPDATE price_action
            SET status = 'CANCELLED',
                cancel_reason = :cancelReason,
                updated_at = now()
            WHERE id = :id AND status = :expectedStatus
            """;

    private static final String CAS_SUPERSEDE = """
            UPDATE price_action
            SET status = 'SUPERSEDED',
                superseded_by_action_id = :supersedingActionId,
                updated_at = now()
            WHERE id = :id AND status = :expectedStatus
            """;

    private static final String CAS_RETRY_SCHEDULED = """
            UPDATE price_action
            SET status = 'RETRY_SCHEDULED',
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                updated_at = now()
            WHERE id = :id AND status = 'EXECUTING'
            """;

    private static final String CAS_SUCCEED = """
            UPDATE price_action
            SET status = 'SUCCEEDED',
                reconciliation_source = :reconciliationSource,
                manual_override_reason = :manualOverrideReason,
                updated_at = now()
            WHERE id = :id AND status = :expectedStatus
            """;

    private static final String CAS_FAIL = """
            UPDATE price_action
            SET status = 'FAILED',
                attempt_count = :attemptCount,
                updated_at = now()
            WHERE id = :id AND status = :expectedStatus
            """;

    private static final String CAS_INCREMENT_ATTEMPT = """
            UPDATE price_action
            SET attempt_count = attempt_count + 1,
                updated_at = now()
            WHERE id = :id AND status = 'EXECUTING'
            """;

    public int casTransition(long id, ActionStatus expectedStatus, ActionStatus newStatus) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("newStatus", newStatus.name());
        return jdbc.update(CAS_STATUS, params);
    }

    public int casApprove(long id, ActionStatus expectedStatus, Long userId) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("userId", userId);
        return jdbc.update(CAS_APPROVE, params);
    }

    public int casHold(long id, String holdReason) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("holdReason", holdReason);
        return jdbc.update(CAS_HOLD, params);
    }

    public int casCancel(long id, ActionStatus expectedStatus, String cancelReason) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("cancelReason", cancelReason);
        return jdbc.update(CAS_CANCEL, params);
    }

    public int casSupersede(long id, ActionStatus expectedStatus, long supersedingActionId) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("supersedingActionId", supersedingActionId);
        return jdbc.update(CAS_SUPERSEDE, params);
    }

    public int casRetryScheduled(long id, int attemptCount, OffsetDateTime nextAttemptAt) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("attemptCount", attemptCount)
                .addValue("nextAttemptAt", nextAttemptAt);
        return jdbc.update(CAS_RETRY_SCHEDULED, params);
    }

    public int casSucceed(long id, ActionStatus expectedStatus,
                          ActionReconciliationSource reconciliationSource,
                          String manualOverrideReason) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("reconciliationSource", reconciliationSource.name())
                .addValue("manualOverrideReason", manualOverrideReason);
        return jdbc.update(CAS_SUCCEED, params);
    }

    public int casFail(long id, ActionStatus expectedStatus, int attemptCount) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("attemptCount", attemptCount);
        return jdbc.update(CAS_FAIL, params);
    }

    public int casIncrementAttempt(long id) {
        var params = new MapSqlParameterSource().addValue("id", id);
        return jdbc.update(CAS_INCREMENT_ATTEMPT, params);
    }
}
