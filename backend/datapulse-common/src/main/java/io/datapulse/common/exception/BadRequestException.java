package io.datapulse.common.exception;

public class BadRequestException extends AppException {

    private BadRequestException(String messageKey, Object... args) {
        super(messageKey, 400, args);
    }

    private BadRequestException(String messageKey, Throwable cause, Object... args) {
        super(messageKey, 400, cause, args);
    }

    public static BadRequestException of(String messageKey, Object... args) {
        return new BadRequestException(messageKey, args);
    }

    public static BadRequestException of(String messageKey, Throwable cause, Object... args) {
        return new BadRequestException(messageKey, cause, args);
    }

    public static BadRequestException validationFailed(String field) {
        return new BadRequestException("validation.failed", field);
    }
}
