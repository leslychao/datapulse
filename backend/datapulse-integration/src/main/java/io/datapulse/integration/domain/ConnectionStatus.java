package io.datapulse.integration.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ConnectionStatus {
    PENDING_VALIDATION,
    ACTIVE,
    AUTH_FAILED,
    DISABLED,
    ARCHIVED;

    private static final Set<ConnectionStatus> TERMINAL = EnumSet.of(ARCHIVED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
