package io.datapulse.common.exception;

import io.datapulse.common.error.MessageCodes;

public class NotFoundException extends AppException {

    private NotFoundException(String messageKey, Object... args) {
        super(messageKey, 404, args);
    }

    public static NotFoundException of(String messageKey, Object... args) {
        return new NotFoundException(messageKey, args);
    }

    public static NotFoundException entity(String entityName, Object id) {
        return new NotFoundException(MessageCodes.ENTITY_NOT_FOUND, entityName, id);
    }

    public static NotFoundException workspace(Long workspaceId) {
        return new NotFoundException(MessageCodes.WORKSPACE_NOT_FOUND, workspaceId);
    }

    public static NotFoundException connection(Long connectionId) {
        return new NotFoundException(MessageCodes.CONNECTION_NOT_FOUND, connectionId);
    }
}
