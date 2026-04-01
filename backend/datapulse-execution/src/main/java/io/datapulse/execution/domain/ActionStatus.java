package io.datapulse.execution.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ActionStatus {

    PENDING_APPROVAL,
    APPROVED,
    ON_HOLD,
    SCHEDULED,
    EXECUTING,
    RECONCILIATION_PENDING,
    RETRY_SCHEDULED,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED,
    SUPERSEDED;

    private static final Set<ActionStatus> TERMINAL_STATES = EnumSet.of(
            SUCCEEDED, FAILED, EXPIRED, CANCELLED, SUPERSEDED
    );

    private static final Set<ActionStatus> PRE_EXECUTION_STATES = EnumSet.of(
            PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED
    );

    private static final Set<ActionStatus> IN_FLIGHT_STATES = EnumSet.of(
            EXECUTING, RETRY_SCHEDULED, RECONCILIATION_PENDING
    );

    private static final Set<ActionStatus> CANCELLABLE_STATES = EnumSet.of(
            PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED, RETRY_SCHEDULED, RECONCILIATION_PENDING
    );

    private static final Set<ActionStatus> SUPERSEDABLE_STATES = EnumSet.of(
            PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED
    );

    private static final Map<ActionStatus, Set<ActionStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(PENDING_APPROVAL, EnumSet.of(APPROVED, EXPIRED, CANCELLED, SUPERSEDED)),
            Map.entry(APPROVED, EnumSet.of(SCHEDULED, ON_HOLD, CANCELLED, SUPERSEDED)),
            Map.entry(ON_HOLD, EnumSet.of(APPROVED, CANCELLED, SUPERSEDED)),
            Map.entry(SCHEDULED, EnumSet.of(EXECUTING, CANCELLED, SUPERSEDED)),
            Map.entry(EXECUTING, EnumSet.of(RECONCILIATION_PENDING, SUCCEEDED, RETRY_SCHEDULED, FAILED)),
            Map.entry(RECONCILIATION_PENDING, EnumSet.of(SUCCEEDED, FAILED, CANCELLED)),
            Map.entry(RETRY_SCHEDULED, EnumSet.of(EXECUTING, CANCELLED)),
            Map.entry(SUCCEEDED, EnumSet.noneOf(ActionStatus.class)),
            Map.entry(FAILED, EnumSet.noneOf(ActionStatus.class)),
            Map.entry(EXPIRED, EnumSet.noneOf(ActionStatus.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(ActionStatus.class)),
            Map.entry(SUPERSEDED, EnumSet.noneOf(ActionStatus.class))
    );

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    public boolean isPreExecution() {
        return PRE_EXECUTION_STATES.contains(this);
    }

    public boolean isInFlight() {
        return IN_FLIGHT_STATES.contains(this);
    }

    public boolean isCancellable() {
        return CANCELLABLE_STATES.contains(this);
    }

    public boolean isSupersedable() {
        return SUPERSEDABLE_STATES.contains(this);
    }

    public boolean canTransitionTo(ActionStatus target) {
        Set<ActionStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public static Set<ActionStatus> terminalStatuses() {
        return TERMINAL_STATES;
    }

    public static Set<ActionStatus> preExecutionStatuses() {
        return PRE_EXECUTION_STATES;
    }

    public static Set<ActionStatus> inFlightStatuses() {
        return IN_FLIGHT_STATES;
    }
}
