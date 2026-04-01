package io.datapulse.execution.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires PENDING_APPROVAL actions past their approval_timeout_hours.
 * Runs hourly as a safety net; normal supersede flow handles most cases.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirationJob {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String EXPIRE_SQL = """
            UPDATE price_action
            SET status = 'EXPIRED', updated_at = now()
            WHERE status = 'PENDING_APPROVAL'
              AND created_at + (approval_timeout_hours || ' hours')::interval < now()
            """;

    @Scheduled(fixedDelayString = "${datapulse.execution.expiration-interval:PT1H}")
    public void expirePendingActions() {
        int expired = jdbc.update(EXPIRE_SQL, new MapSqlParameterSource());
        if (expired > 0) {
            log.info("Expired {} PENDING_APPROVAL actions past approval timeout", expired);
        }
    }
}
