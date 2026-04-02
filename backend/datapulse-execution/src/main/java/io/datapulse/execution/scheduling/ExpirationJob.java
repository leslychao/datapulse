package io.datapulse.execution.scheduling;

import io.datapulse.execution.domain.ActionService;
import io.datapulse.platform.observability.MetricsFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Expires PENDING_APPROVAL actions past their approval_timeout_hours.
 * Runs hourly as a safety net; normal supersede flow handles most cases.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirationJob {

    private final NamedParameterJdbcTemplate jdbc;
    private final ActionService actionService;
    private final MetricsFacade metrics;

    private static final String FIND_EXPIRED_SQL = """
            SELECT id FROM price_action
            WHERE status = 'PENDING_APPROVAL'
              AND created_at + (approval_timeout_hours || ' hours')::interval < now()
            """;

    @Scheduled(fixedDelayString = "${datapulse.execution.expiration-interval:PT1H}")
    @SchedulerLock(name = "execution_expirationJob", lockAtMostFor = "PT30M")
    public void expirePendingActions() {
        List<Long> ids = jdbc.query(FIND_EXPIRED_SQL,
                new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getLong("id"));

        int expired = 0;
        for (long actionId : ids) {
            try {
                actionService.casExpire(actionId);
                expired++;
            } catch (Exception e) {
                log.error("Failed to expire action: actionId={}", actionId, e);
            }
        }

        metrics.incrementCounter("execution.expiration_job.runs");
        if (expired > 0) {
            log.info("Expired {} PENDING_APPROVAL actions past approval timeout", expired);
        }
    }
}
