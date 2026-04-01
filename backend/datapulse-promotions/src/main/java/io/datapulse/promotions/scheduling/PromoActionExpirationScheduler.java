package io.datapulse.promotions.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromoActionExpirationScheduler {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Scheduled(fixedDelayString = "${datapulse.promo.expiration-check-interval-ms:60000}")
  @SchedulerLock(name = "promoActionExpiration", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
  public void expireFrozenActions() {
    try {
      int expired = jdbcTemplate.update("""
          UPDATE promo_action
          SET status = 'EXPIRED', updated_at = NOW()
          WHERE status IN ('PENDING_APPROVAL', 'APPROVED')
            AND freeze_at_snapshot IS NOT NULL
            AND freeze_at_snapshot < NOW()
          """, Map.of());

      if (expired > 0) {
        log.info("Expired promo actions past freeze_at deadline: count={}", expired);
      }
    } catch (Exception e) {
      log.error("Promo action expiration check failed", e);
    }
  }

  @Scheduled(fixedDelayString = "${datapulse.promo.stuck-check-interval-ms:300000}")
  @SchedulerLock(name = "promoActionStuckDetector", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
  public void detectStuckActions() {
    try {
      int failedExecuting = jdbcTemplate.update("""
          UPDATE promo_action
          SET status = 'FAILED', last_error = 'Stuck in EXECUTING state', updated_at = NOW()
          WHERE status = 'EXECUTING'
            AND updated_at < NOW() - INTERVAL '5 minutes'
          """, Map.of());

      int failedApproved = jdbcTemplate.update("""
          UPDATE promo_action
          SET status = 'FAILED', last_error = 'Stuck in APPROVED state', updated_at = NOW()
          WHERE status = 'APPROVED'
            AND updated_at < NOW() - INTERVAL '5 minutes'
          """, Map.of());

      if (failedExecuting + failedApproved > 0) {
        log.warn("Detected stuck promo actions: executing={}, approved={}",
            failedExecuting, failedApproved);
      }
    } catch (Exception e) {
      log.error("Promo stuck-state detection failed", e);
    }
  }
}
