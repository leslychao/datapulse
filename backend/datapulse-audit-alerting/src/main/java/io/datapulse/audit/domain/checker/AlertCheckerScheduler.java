package io.datapulse.audit.domain.checker;

import java.util.List;

import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.domain.AlertRuleType;
import io.datapulse.audit.persistence.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertCheckerScheduler {

    private final AlertCheckerRegistry checkerRegistry;
    private final AlertRuleRepository alertRuleRepository;

    @Scheduled(fixedDelayString = "${datapulse.alerting.stale-data-interval:PT5M}")
    @SchedulerLock(name = "alertChecker-staleData", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void runStaleDataChecker() {
        runCheckerForType(AlertRuleType.STALE_DATA.name());
    }

    @Scheduled(fixedDelayString = "${datapulse.alerting.missing-sync-interval:PT15M}")
    @SchedulerLock(name = "alertChecker-missingSync", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void runMissingSyncChecker() {
        runCheckerForType(AlertRuleType.MISSING_SYNC.name());
    }

    @Scheduled(fixedDelayString = "${datapulse.alerting.mismatch-interval:PT30M}")
    @SchedulerLock(name = "alertChecker-mismatch", lockAtMostFor = "PT15M", lockAtLeastFor = "PT2M")
    public void runMismatchChecker() {
        runCheckerForType(AlertRuleType.MISMATCH.name());
    }

    private void runCheckerForType(String ruleType) {
        AlertChecker checker = checkerRegistry.getChecker(ruleType);
        if (checker == null) {
            log.debug("No checker registered for ruleType={}", ruleType);
            return;
        }

        List<AlertRuleResponse> enabledRules = alertRuleRepository.findEnabledByRuleType(ruleType);
        if (enabledRules.isEmpty()) {
            log.debug("No enabled rules for ruleType={}", ruleType);
            return;
        }

        for (AlertRuleResponse rule : enabledRules) {
            try {
                checker.check(rule);
            } catch (Exception e) {
                log.error("Alert checker failed: ruleType={}, ruleId={}, workspaceId={}, error={}",
                        ruleType, rule.id(), rule.workspaceId(), e.getMessage(), e);
            }
        }
    }
}
