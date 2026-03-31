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
    private static final Set<ConnectionStatus> ALLOWS_VALIDATION = EnumSet.of(PENDING_VALIDATION, ACTIVE, AUTH_FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean allowsValidation() {
        return ALLOWS_VALIDATION.contains(this);
    }
}
