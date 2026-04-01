package io.datapulse.audit.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AlertEventStatus {

    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    AUTO_RESOLVED;

    private static final Set<AlertEventStatus> ACTIVE = EnumSet.of(OPEN, ACKNOWLEDGED);
    private static final Set<AlertEventStatus> TERMINAL = EnumSet.of(RESOLVED, AUTO_RESOLVED);

    public static Set<AlertEventStatus> activeStatuses() {
        return ACTIVE;
    }

    public static Set<AlertEventStatus> terminalStatuses() {
        return TERMINAL;
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
