package io.datapulse.sellerops.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentCleanupScheduler {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String CLEANUP_SQL = """
            DELETE FROM working_queue_assignment
            WHERE status IN ('DONE', 'DISMISSED')
              AND updated_at < NOW() - INTERVAL '30 days'
            """;

    @Scheduled(cron = "${datapulse.queue.cleanup-cron:0 0 3 * * *}")
    @SchedulerLock(name = "assignmentCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cleanupOldAssignments() {
        try {
            int deleted = jdbc.update(CLEANUP_SQL, new MapSqlParameterSource());
            if (deleted > 0) {
                log.info("Cleaned up old queue assignments: deleted={}", deleted);
            }
        } catch (Exception e) {
            log.error("Assignment cleanup failed: error={}", e.getMessage(), e);
        }
    }
}
