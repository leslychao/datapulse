package io.datapulse.audit.domain;

import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.api.UpdateAlertRuleRequest;
import io.datapulse.audit.persistence.AlertRuleRepository;
import io.datapulse.common.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

  @Mock
  private AlertRuleRepository alertRuleRepository;

  @InjectMocks
  private AlertRuleService alertRuleService;

  private AlertRuleResponse buildRule(long id, boolean enabled) {
    return new AlertRuleResponse(
        id, 1L, "STALE_DATA", "connection", 10L,
        "{\"hours\":24}", enabled, "WARNING", true,
        OffsetDateTime.now(), OffsetDateTime.now());
  }

  @Nested
  @DisplayName("listRules")
  class ListRules {

    @Test
    @DisplayName("should_return_all_rules_for_workspace")
    void should_return_all_rules_for_workspace() {
      var rule = buildRule(1L, true);
      when(alertRuleRepository.findByWorkspaceId(1L)).thenReturn(List.of(rule));

      List<AlertRuleResponse> result = alertRuleService.listRules(1L);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).ruleType()).isEqualTo("STALE_DATA");
    }
  }

  @Nested
  @DisplayName("getRule")
  class GetRule {

    @Test
    @DisplayName("should_return_rule_when_found")
    void should_return_rule_when_found() {
      var rule = buildRule(1L, true);
      when(alertRuleRepository.findById(1L, 1L)).thenReturn(Optional.of(rule));

      AlertRuleResponse result = alertRuleService.getRule(1L, 1L);

      assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_throw_not_found_when_missing")
    void should_throw_not_found_when_missing() {
      when(alertRuleRepository.findById(99L, 1L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> alertRuleService.getRule(99L, 1L))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("updateRule")
  class UpdateRule {

    @Test
    @DisplayName("should_update_and_return_rule_when_exists")
    void should_update_and_return_rule_when_exists() {
      var existing = buildRule(1L, true);
      var updated = buildRule(1L, false);
      when(alertRuleRepository.findById(1L, 1L))
          .thenReturn(Optional.of(existing))
          .thenReturn(Optional.of(updated));

      AlertRuleResponse result = alertRuleService.updateRule(1L, 1L,
          new UpdateAlertRuleRequest("{\"hours\":48}", false, "CRITICAL", false));

      verify(alertRuleRepository).update(1L, 1L,
          "{\"hours\":48}", false, "CRITICAL", false);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should_throw_not_found_when_rule_missing")
    void should_throw_not_found_when_rule_missing() {
      when(alertRuleRepository.findById(99L, 1L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> alertRuleService.updateRule(99L, 1L,
          new UpdateAlertRuleRequest("{}", true, "WARNING", false)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("activate / deactivate")
  class ActivateDeactivate {

    @Test
    @DisplayName("should_activate_rule_when_exists")
    void should_activate_rule_when_exists() {
      var rule = buildRule(1L, false);
      var activated = buildRule(1L, true);
      when(alertRuleRepository.findById(1L, 1L))
          .thenReturn(Optional.of(rule))
          .thenReturn(Optional.of(activated));

      AlertRuleResponse result = alertRuleService.activate(1L, 1L);

      verify(alertRuleRepository).setEnabled(1L, 1L, true);
    }

    @Test
    @DisplayName("should_deactivate_rule_when_exists")
    void should_deactivate_rule_when_exists() {
      var rule = buildRule(1L, true);
      var deactivated = buildRule(1L, false);
      when(alertRuleRepository.findById(1L, 1L))
          .thenReturn(Optional.of(rule))
          .thenReturn(Optional.of(deactivated));

      alertRuleService.deactivate(1L, 1L);

      verify(alertRuleRepository).setEnabled(1L, 1L, false);
    }
  }
}
