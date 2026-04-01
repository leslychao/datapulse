package io.datapulse.audit.domain.checker;

import io.datapulse.audit.api.AlertRuleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertCheckerRegistryTest {

  private AlertChecker stubChecker(String ruleType) {
    return new AlertChecker() {
      @Override
      public String ruleType() {
        return ruleType;
      }

      @Override
      public void check(AlertRuleResponse rule) {
      }
    };
  }

  @Nested
  @DisplayName("getChecker")
  class GetChecker {

    @Test
    @DisplayName("should_return_checker_when_registered")
    void should_return_checker_when_registered() {
      var registry = new AlertCheckerRegistry(
          List.of(stubChecker("STALE_DATA"), stubChecker("MISSING_SYNC")));

      AlertChecker checker = registry.getChecker("STALE_DATA");

      assertThat(checker).isNotNull();
      assertThat(checker.ruleType()).isEqualTo("STALE_DATA");
    }

    @Test
    @DisplayName("should_return_null_when_not_registered")
    void should_return_null_when_not_registered() {
      var registry = new AlertCheckerRegistry(List.of(stubChecker("STALE_DATA")));

      AlertChecker checker = registry.getChecker("UNKNOWN_TYPE");

      assertThat(checker).isNull();
    }
  }

  @Nested
  @DisplayName("hasChecker")
  class HasChecker {

    @Test
    @DisplayName("should_return_true_when_checker_exists")
    void should_return_true_when_checker_exists() {
      var registry = new AlertCheckerRegistry(List.of(stubChecker("MISMATCH")));

      assertThat(registry.hasChecker("MISMATCH")).isTrue();
    }

    @Test
    @DisplayName("should_return_false_when_checker_not_exists")
    void should_return_false_when_checker_not_exists() {
      var registry = new AlertCheckerRegistry(List.of(stubChecker("MISMATCH")));

      assertThat(registry.hasChecker("STALE_DATA")).isFalse();
    }
  }

  @Nested
  @DisplayName("constructor")
  class Constructor {

    @Test
    @DisplayName("should_throw_when_duplicate_rule_types")
    void should_throw_when_duplicate_rule_types() {
      assertThatThrownBy(() -> new AlertCheckerRegistry(
          List.of(stubChecker("STALE_DATA"), stubChecker("STALE_DATA"))))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
