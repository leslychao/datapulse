package io.datapulse.domain.exception;

import java.io.Serial;
import java.util.Arrays;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
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
    this(null, status, messageKey, args);
  }

  public AppException(Throwable cause, String messageKey, Object... args) {
    this(cause, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, args);
  }

  public AppException(Throwable cause, HttpStatus status, String messageKey, Object... args) {
    super(cause);
    this.status = status;
    this.messageKey = messageKey;
    this.args = sanitizeArgs(args);
  }

  private static Object[] sanitizeArgs(Object[] args) {
    if (ArrayUtils.isEmpty(args)) {
      return args;
    }
    return Arrays.stream(args)
        .map(arg -> arg instanceof Number ? String.valueOf(arg) : arg)
        .toArray();
  }
}
