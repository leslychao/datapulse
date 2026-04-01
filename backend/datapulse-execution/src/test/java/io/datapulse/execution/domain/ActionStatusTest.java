package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ActionStatus")
class ActionStatusTest {

  private static final Set<ActionStatus> ALL = EnumSet.allOf(ActionStatus.class);

  @Nested
  @DisplayName("isTerminal")
  class IsTerminal {

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"SUCCEEDED", "FAILED", "EXPIRED", "CANCELLED", "SUPERSEDED"})
    @DisplayName("should return true for terminal states")
    void should_returnTrue_when_stateIsTerminal(ActionStatus status) {
      assertThat(status.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"SUCCEEDED", "FAILED", "EXPIRED", "CANCELLED", "SUPERSEDED"},
        mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("should return false for non-terminal states")
    void should_returnFalse_when_stateIsNotTerminal(ActionStatus status) {
      assertThat(status.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("isPreExecution")
  class IsPreExecution {

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"PENDING_APPROVAL", "APPROVED", "ON_HOLD", "SCHEDULED"})
    @DisplayName("should return true for pre-execution states")
    void should_returnTrue_when_stateIsPreExecution(ActionStatus status) {
      assertThat(status.isPreExecution()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"PENDING_APPROVAL", "APPROVED", "ON_HOLD", "SCHEDULED"},
        mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("should return false for non-pre-execution states")
    void should_returnFalse_when_stateIsNotPreExecution(ActionStatus status) {
      assertThat(status.isPreExecution()).isFalse();
    }
  }

  @Nested
  @DisplayName("isInFlight")
  class IsInFlight {

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"EXECUTING", "RETRY_SCHEDULED", "RECONCILIATION_PENDING"})
    @DisplayName("should return true for in-flight states")
    void should_returnTrue_when_stateIsInFlight(ActionStatus status) {
      assertThat(status.isInFlight()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"EXECUTING", "RETRY_SCHEDULED", "RECONCILIATION_PENDING"},
        mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("should return false for non-in-flight states")
    void should_returnFalse_when_stateIsNotInFlight(ActionStatus status) {
      assertThat(status.isInFlight()).isFalse();
    }
  }

  @Nested
  @DisplayName("isCancellable")
  class IsCancellable {

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"PENDING_APPROVAL", "APPROVED", "ON_HOLD", "SCHEDULED",
            "RETRY_SCHEDULED", "RECONCILIATION_PENDING"})
    @DisplayName("should return true for cancellable states")
    void should_returnTrue_when_stateIsCancellable(ActionStatus status) {
      assertThat(status.isCancellable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"EXECUTING", "SUCCEEDED", "FAILED", "EXPIRED", "CANCELLED", "SUPERSEDED"})
    @DisplayName("should return false for non-cancellable states")
    void should_returnFalse_when_stateIsNotCancellable(ActionStatus status) {
      assertThat(status.isCancellable()).isFalse();
    }
  }

  @Nested
  @DisplayName("isSupersedable")
  class IsSupersedable {

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"PENDING_APPROVAL", "APPROVED", "ON_HOLD", "SCHEDULED"})
    @DisplayName("should return true for supersedable states")
    void should_returnTrue_when_stateIsSupersedable(ActionStatus status) {
      assertThat(status.isSupersedable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"PENDING_APPROVAL", "APPROVED", "ON_HOLD", "SCHEDULED"},
        mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("should return false for non-supersedable states")
    void should_returnFalse_when_stateIsNotSupersedable(ActionStatus status) {
      assertThat(status.isSupersedable()).isFalse();
    }
  }

  @Nested
  @DisplayName("canTransitionTo — allowed transitions")
  class AllowedTransitions {

    @Test
    @DisplayName("PENDING_APPROVAL → APPROVED, EXPIRED, CANCELLED, SUPERSEDED")
    void should_allowTransitions_when_pendingApproval() {
      assertTransitionsAllowed(ActionStatus.PENDING_APPROVAL,
          ActionStatus.APPROVED, ActionStatus.EXPIRED,
          ActionStatus.CANCELLED, ActionStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("APPROVED → SCHEDULED, ON_HOLD, CANCELLED, SUPERSEDED")
    void should_allowTransitions_when_approved() {
      assertTransitionsAllowed(ActionStatus.APPROVED,
          ActionStatus.SCHEDULED, ActionStatus.ON_HOLD,
          ActionStatus.CANCELLED, ActionStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("ON_HOLD → APPROVED, CANCELLED, SUPERSEDED")
    void should_allowTransitions_when_onHold() {
      assertTransitionsAllowed(ActionStatus.ON_HOLD,
          ActionStatus.APPROVED, ActionStatus.CANCELLED, ActionStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("SCHEDULED → EXECUTING, CANCELLED, SUPERSEDED")
    void should_allowTransitions_when_scheduled() {
      assertTransitionsAllowed(ActionStatus.SCHEDULED,
          ActionStatus.EXECUTING, ActionStatus.CANCELLED, ActionStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("EXECUTING → RECONCILIATION_PENDING, SUCCEEDED, RETRY_SCHEDULED, FAILED")
    void should_allowTransitions_when_executing() {
      assertTransitionsAllowed(ActionStatus.EXECUTING,
          ActionStatus.RECONCILIATION_PENDING, ActionStatus.SUCCEEDED,
          ActionStatus.RETRY_SCHEDULED, ActionStatus.FAILED);
    }

    @Test
    @DisplayName("RECONCILIATION_PENDING → SUCCEEDED, FAILED, CANCELLED")
    void should_allowTransitions_when_reconciliationPending() {
      assertTransitionsAllowed(ActionStatus.RECONCILIATION_PENDING,
          ActionStatus.SUCCEEDED, ActionStatus.FAILED, ActionStatus.CANCELLED);
    }

    @Test
    @DisplayName("RETRY_SCHEDULED → EXECUTING, CANCELLED")
    void should_allowTransitions_when_retryScheduled() {
      assertTransitionsAllowed(ActionStatus.RETRY_SCHEDULED,
          ActionStatus.EXECUTING, ActionStatus.CANCELLED);
    }
  }

  @Nested
  @DisplayName("canTransitionTo — terminal states have no outgoing transitions")
  class TerminalStatesNoTransitions {

    @ParameterizedTest
    @EnumSource(value = ActionStatus.class,
        names = {"SUCCEEDED", "FAILED", "EXPIRED", "CANCELLED", "SUPERSEDED"})
    @DisplayName("should reject all transitions from terminal states")
    void should_rejectAllTransitions_when_stateIsTerminal(ActionStatus terminal) {
      for (ActionStatus target : ALL) {
        assertThat(terminal.canTransitionTo(target))
            .as("%s → %s should be rejected", terminal, target)
            .isFalse();
      }
    }
  }

  @Nested
  @DisplayName("canTransitionTo — disallowed transitions")
  class DisallowedTransitions {

    @Test
    @DisplayName("PENDING_APPROVAL cannot transition to EXECUTING")
    void should_rejectTransition_when_pendingApprovalToExecuting() {
      assertThat(ActionStatus.PENDING_APPROVAL.canTransitionTo(ActionStatus.EXECUTING))
          .isFalse();
    }

    @Test
    @DisplayName("APPROVED cannot transition to FAILED")
    void should_rejectTransition_when_approvedToFailed() {
      assertThat(ActionStatus.APPROVED.canTransitionTo(ActionStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("EXECUTING cannot transition to APPROVED")
    void should_rejectTransition_when_executingToApproved() {
      assertThat(ActionStatus.EXECUTING.canTransitionTo(ActionStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("EXECUTING cannot transition to CANCELLED")
    void should_rejectTransition_when_executingToCancelled() {
      assertThat(ActionStatus.EXECUTING.canTransitionTo(ActionStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("RETRY_SCHEDULED cannot transition to SUCCEEDED")
    void should_rejectTransition_when_retryScheduledToSucceeded() {
      assertThat(ActionStatus.RETRY_SCHEDULED.canTransitionTo(ActionStatus.SUCCEEDED))
          .isFalse();
    }

    @Test
    @DisplayName("Self-transition is never allowed")
    void should_rejectSelfTransition_when_sameState() {
      for (ActionStatus status : ALL) {
        assertThat(status.canTransitionTo(status))
            .as("%s → %s should be rejected", status, status)
            .isFalse();
      }
    }
  }

  @Nested
  @DisplayName("Static factory methods")
  class StaticFactories {

    @Test
    @DisplayName("terminalStatuses returns exactly 5 terminal states")
    void should_returnFiveTerminals_when_callingTerminalStatuses() {
      assertThat(ActionStatus.terminalStatuses())
          .containsExactlyInAnyOrder(
              ActionStatus.SUCCEEDED, ActionStatus.FAILED,
              ActionStatus.EXPIRED, ActionStatus.CANCELLED,
              ActionStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("preExecutionStatuses returns exactly 4 pre-execution states")
    void should_returnFourPreExecution_when_callingPreExecutionStatuses() {
      assertThat(ActionStatus.preExecutionStatuses())
          .containsExactlyInAnyOrder(
              ActionStatus.PENDING_APPROVAL, ActionStatus.APPROVED,
              ActionStatus.ON_HOLD, ActionStatus.SCHEDULED);
    }

    @Test
    @DisplayName("inFlightStatuses returns exactly 3 in-flight states")
    void should_returnThreeInFlight_when_callingInFlightStatuses() {
      assertThat(ActionStatus.inFlightStatuses())
          .containsExactlyInAnyOrder(
              ActionStatus.EXECUTING, ActionStatus.RETRY_SCHEDULED,
              ActionStatus.RECONCILIATION_PENDING);
    }
  }

  @Nested
  @DisplayName("Completeness")
  class Completeness {

    @Test
    @DisplayName("every status belongs to exactly one category: pre-execution, in-flight, or terminal")
    void should_categorizeAllStatuses_when_checkingCategories() {
      for (ActionStatus status : ALL) {
        int count = (status.isPreExecution() ? 1 : 0)
            + (status.isInFlight() ? 1 : 0)
            + (status.isTerminal() ? 1 : 0);

        assertThat(count)
            .as("%s should belong to exactly one category", status)
            .isEqualTo(1);
      }
    }
  }

  private void assertTransitionsAllowed(ActionStatus from, ActionStatus... allowedTargets) {
    Set<ActionStatus> allowed = EnumSet.noneOf(ActionStatus.class);
    for (ActionStatus t : allowedTargets) {
      allowed.add(t);
    }

    for (ActionStatus target : ALL) {
      if (allowed.contains(target)) {
        assertThat(from.canTransitionTo(target))
            .as("%s → %s should be allowed", from, target)
            .isTrue();
      } else {
        assertThat(from.canTransitionTo(target))
            .as("%s → %s should be rejected", from, target)
            .isFalse();
      }
    }
  }
}
