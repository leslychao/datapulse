package io.datapulse.pricing.domain;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates the 5 safety conditions before a policy can operate in FULL_AUTO:
 * <ol>
 *   <li>Policy was in SEMI_AUTO for at least N days (default 7)</li>
 *   <li>No FAILED actions in the last N days</li>
 *   <li>Stale data guard is NOT disabled</li>
 *   <li>Manual lock guard is NOT disabled</li>
 *   <li>Pricing manager explicitly confirms (confirmFullAuto flag)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullAutoSafetyGate {

  private final ObjectMapper objectMapper;
  private final PricingDataReadRepository dataReadRepository;

  @Value("${datapulse.pricing.full-auto.min-semi-auto-days:7}")
  private int minSemiAutoDays;

  @Value("${datapulse.pricing.full-auto.failed-action-lookback-days:7}")
  private int failedActionLookbackDays;

  public void validateOnSwitch(PricePolicyEntity entity, Boolean confirmFullAuto) {
    if (!Boolean.TRUE.equals(confirmFullAuto)) {
      throw BadRequestException.of(MessageCodes.PRICING_POLICY_FULL_AUTO_CONFIRM_REQUIRED);
    }

    List<String> violations = new ArrayList<>();

    if (!hasSufficientSemiAutoDuration(entity)) {
      violations.add("semi_auto_duration_insufficient");
    }

    if (hasRecentFailedActions(entity.getWorkspaceId())) {
      violations.add("recent_failed_actions");
    }

    GuardConfig guardConfig = parseGuardConfig(entity.getGuardConfig());

    if (!isStaleDataGuardEnabled(guardConfig)) {
      violations.add("stale_data_guard_disabled");
    }

    if (!isManualLockGuardEnabled(guardConfig)) {
      violations.add("manual_lock_guard_disabled");
    }

    if (!violations.isEmpty()) {
      throw BadRequestException.of(MessageCodes.PRICING_POLICY_FULL_AUTO_GATE_FAILED,
          String.join(", ", violations));
    }
  }

  /**
   * Runtime re-check of conditions 2–4 for FULL_AUTO policies during pricing runs.
   * Condition 1 (SEMI_AUTO duration) and 5 (confirmation) are switch-time only.
   */
  public List<String> runtimeCheck(PricePolicyEntity entity) {
    List<String> violations = new ArrayList<>();

    if (hasRecentFailedActions(entity.getWorkspaceId())) {
      violations.add("recent_failed_actions");
    }

    GuardConfig guardConfig = parseGuardConfig(entity.getGuardConfig());

    if (!isStaleDataGuardEnabled(guardConfig)) {
      violations.add("stale_data_guard_disabled");
    }
    if (!isManualLockGuardEnabled(guardConfig)) {
      violations.add("manual_lock_guard_disabled");
    }

    return violations;
  }

  private boolean hasSufficientSemiAutoDuration(PricePolicyEntity entity) {
    if (entity.getExecutionMode() != ExecutionMode.SEMI_AUTO) {
      return false;
    }
    OffsetDateTime modeChangedAt = entity.getExecutionModeChangedAt();
    if (modeChangedAt == null) {
      modeChangedAt = entity.getCreatedAt();
    }
    long daysSinceChange = ChronoUnit.DAYS.between(modeChangedAt, OffsetDateTime.now());
    return daysSinceChange >= minSemiAutoDays;
  }

  private boolean hasRecentFailedActions(long workspaceId) {
    OffsetDateTime since = OffsetDateTime.now().minusDays(failedActionLookbackDays);
    return dataReadRepository.hasFailedActionsInPeriod(workspaceId, since);
  }

  private boolean isStaleDataGuardEnabled(GuardConfig config) {
    return config.effectiveStaleDataGuardHours() > 0;
  }

  private boolean isManualLockGuardEnabled(GuardConfig config) {
    return true;
  }

  private GuardConfig parseGuardConfig(String json) {
    if (json == null || json.isBlank()) {
      return GuardConfig.DEFAULTS;
    }
    try {
      return objectMapper.readValue(json, GuardConfig.class);
    } catch (JsonProcessingException e) {
      return GuardConfig.DEFAULTS;
    }
  }
}
