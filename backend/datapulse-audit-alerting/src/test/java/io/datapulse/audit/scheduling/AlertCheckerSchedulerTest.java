package io.datapulse.audit.scheduling;

import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.domain.checker.AlertChecker;
import io.datapulse.audit.domain.checker.AlertCheckerRegistry;
import io.datapulse.audit.persistence.AlertRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertCheckerSchedulerTest {

  @Mock
  private AlertCheckerRegistry checkerRegistry;
  @Mock
  private AlertRuleRepository alertRuleRepository;

  @InjectMocks
  private AlertCheckerScheduler scheduler;

  private AlertRuleResponse buildRule(long id) {
    return new AlertRuleResponse(
        id, 1L, "STALE_DATA", "connection", 10L,
        "{\"hours\":24}", true, "WARNING", true,
        OffsetDateTime.now(), OffsetDateTime.now());
  }

  @Nested
  @DisplayName("runStaleDataChecker")
  class RunStaleDataChecker {

    @Test
    @DisplayName("should_execute_checker_for_each_enabled_rule")
    void should_execute_checker_for_each_enabled_rule() {
      AlertChecker checker = org.mockito.Mockito.mock(AlertChecker.class);
      when(checkerRegistry.getChecker("STALE_DATA")).thenReturn(checker);

      var rule1 = buildRule(1L);
      var rule2 = buildRule(2L);
      when(alertRuleRepository.findEnabledByRuleType("STALE_DATA"))
          .thenReturn(List.of(rule1, rule2));

      scheduler.runStaleDataChecker();

      verify(checker, times(2)).check(any(AlertRuleResponse.class));
    }

    @Test
    @DisplayName("should_skip_when_no_checker_registered")
    void should_skip_when_no_checker_registered() {
      when(checkerRegistry.getChecker("STALE_DATA")).thenReturn(null);

      scheduler.runStaleDataChecker();

      verify(alertRuleRepository, never()).findEnabledByRuleType(any());
    }

    @Test
    @DisplayName("should_skip_when_no_enabled_rules")
    void should_skip_when_no_enabled_rules() {
      AlertChecker checker = org.mockito.Mockito.mock(AlertChecker.class);
      when(checkerRegistry.getChecker("STALE_DATA")).thenReturn(checker);
      when(alertRuleRepository.findEnabledByRuleType("STALE_DATA"))
          .thenReturn(List.of());

      scheduler.runStaleDataChecker();

      verify(checker, never()).check(any());
    }

    @Test
    @DisplayName("should_continue_processing_when_one_rule_fails")
    void should_continue_processing_when_one_rule_fails() {
      AlertChecker checker = org.mockito.Mockito.mock(AlertChecker.class);
      when(checkerRegistry.getChecker("STALE_DATA")).thenReturn(checker);

      var rule1 = buildRule(1L);
      var rule2 = buildRule(2L);
      when(alertRuleRepository.findEnabledByRuleType("STALE_DATA"))
          .thenReturn(List.of(rule1, rule2));

      doThrow(new RuntimeException("DB error"))
          .when(checker).check(rule1);

      assertThatCode(() -> scheduler.runStaleDataChecker())
          .doesNotThrowAnyException();

      verify(checker).check(rule2);
    }
  }

  @Nested
  @DisplayName("runMissingSyncChecker")
  class RunMissingSyncChecker {

    @Test
    @DisplayName("should_delegate_to_registry_with_missing_sync_type")
    void should_delegate_to_registry_with_missing_sync_type() {
      when(checkerRegistry.getChecker("MISSING_SYNC")).thenReturn(null);

      scheduler.runMissingSyncChecker();

      verify(checkerRegistry).getChecker("MISSING_SYNC");
    }
  }

  @Nested
  @DisplayName("runMismatchChecker")
  class RunMismatchChecker {

    @Test
    @DisplayName("should_delegate_to_registry_with_mismatch_type")
    void should_delegate_to_registry_with_mismatch_type() {
      when(checkerRegistry.getChecker("MISMATCH")).thenReturn(null);

      scheduler.runMismatchChecker();

      verify(checkerRegistry).getChecker("MISMATCH");
    }
  }
}
