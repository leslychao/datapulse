package io.datapulse.domain.exception;

import java.io.Serial;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 202510260001L;

  private final String messageKey;
  private final Object[] args;
  private final HttpStatus status;

  public AppException(String messageKey, Object... args) {
    this(HttpStatus.INTERNAL_SERVER_ERROR, messageKey, args);
  }

  public AppException(HttpStatus status, String messageKey, Object... args) {
    this.status = status;
    this.messageKey = messageKey;
    this.args = args;
  }

  public AppException(Throwable cause, HttpStatus status, String messageKey, Object... args) {
    this.status = status;
    this.messageKey = messageKey;
    this.args = args;
  }

  public AppException(Throwable cause, String messageKey, Object... args) {
    this(cause, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, args);
  }
}
