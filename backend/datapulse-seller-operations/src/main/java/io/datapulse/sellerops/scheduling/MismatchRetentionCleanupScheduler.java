package io.datapulse.sellerops.scheduling;

import io.datapulse.sellerops.config.MismatchProperties;
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
public class MismatchRetentionCleanupScheduler {

    private final NamedParameterJdbcTemplate jdbc;
    private final MismatchProperties mismatchProperties;

    private static final String CLEANUP_SQL = """
            DELETE FROM alert_event
            WHERE status IN ('RESOLVED', 'AUTO_RESOLVED')
              AND resolved_at < NOW() - make_interval(days => :retentionDays)
              AND details->>'mismatch_type' IS NOT NULL
            """;

    @Scheduled(cron = "${datapulse.mismatch.retention-cleanup-cron:0 0 4 * * *}")
    @SchedulerLock(name = "mismatchRetentionCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cleanupResolvedMismatches() {
        try {
            var params = new MapSqlParameterSource(
                    "retentionDays", mismatchProperties.getRetentionDays());
            int deleted = jdbc.update(CLEANUP_SQL, params);
            if (deleted > 0) {
                log.info("Mismatch retention cleanup completed: deletedCount={}", deleted);
            }
        } catch (Exception e) {
            log.error("Mismatch retention cleanup failed: error={}", e.getMessage(), e);
        }
    }
}
