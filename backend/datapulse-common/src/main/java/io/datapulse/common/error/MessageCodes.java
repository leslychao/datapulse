package io.datapulse.common.error;

public final class MessageCodes {

    private MessageCodes() {
    }

    // --- General ---
    public static final String INTERNAL_ERROR = "internal.error";
    public static final String VALIDATION_FAILED = "validation.failed";
    public static final String OPERATION_NOT_ALLOWED = "operation.not.allowed";

    // --- Auth ---
    public static final String UNAUTHORIZED = "auth.unauthorized";
    public static final String ACCESS_DENIED = "access.denied";

    // --- Entity lifecycle ---
    public static final String ENTITY_NOT_FOUND = "entity.not.found";
    public static final String DUPLICATE_ENTITY = "entity.duplicate";
    public static final String INVALID_STATE = "entity.invalid.state";

    // --- Workspace / Connection shortcuts ---
    public static final String WORKSPACE_NOT_FOUND = "workspace.not.found";
    public static final String CONNECTION_NOT_FOUND = "connection.not.found";

    // --- Integration ---
    public static final String RATE_LIMITED = "api.rate.limited";
}
