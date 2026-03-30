package io.datapulse.common.exception;

public class NotFoundException extends AppException {

    private NotFoundException(String messageKey, Object... args) {
        super(messageKey, 404, args);
    }

    public static NotFoundException of(String messageKey, Object... args) {
        return new NotFoundException(messageKey, args);
    }

    public static NotFoundException entity(String entityName, Object id) {
        return new NotFoundException("entity.not.found", entityName, id);
    }

    public static NotFoundException workspace(Long workspaceId) {
        return new NotFoundException("workspace.not.found", workspaceId);
    }

    public static NotFoundException connection(Long connectionId) {
        return new NotFoundException("connection.not.found", connectionId);
    }
}
