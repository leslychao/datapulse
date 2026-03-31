package io.datapulse.common.exception;

public class ConflictException extends AppException {

    private ConflictException(String messageKey, Object... args) {
        super(messageKey, 409, args);
    }

    public static ConflictException of(String messageKey, Object... args) {
        return new ConflictException(messageKey, args);
    }
}
