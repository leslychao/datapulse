package io.datapulse.sellerops.scheduling;

import io.datapulse.sellerops.domain.MismatchMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MismatchMonitorScheduler {

    private final MismatchMonitorService mismatchMonitorService;
    private final NamedParameterJdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "${datapulse.mismatch.fallback-interval:PT2H}")
    @SchedulerLock(name = "mismatchMonitor", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void checkMismatches() {
        try {
            List<Long> workspaceIds = jdbc.queryForList(
                    "SELECT DISTINCT id FROM workspace WHERE status = 'ACTIVE'",
                    Map.of(), Long.class);

            for (Long workspaceId : workspaceIds) {
                try {
                    mismatchMonitorService.checkAllMismatches(workspaceId);
                } catch (Exception e) {
                    log.error("Mismatch check failed: workspaceId={}, error={}",
                            workspaceId, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Mismatch monitor job failed: error={}", e.getMessage(), e);
        }
    }
}
