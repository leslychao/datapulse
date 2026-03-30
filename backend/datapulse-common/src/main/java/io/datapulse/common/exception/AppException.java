package io.datapulse.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;
    private final int statusCode;

    public AppException(String messageKey, int statusCode, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.statusCode = statusCode;
        this.args = args;
    }

    public AppException(String messageKey, int statusCode, Throwable cause, Object... args) {
        super(messageKey, cause);
        this.messageKey = messageKey;
        this.statusCode = statusCode;
        this.args = args;
    }
}
