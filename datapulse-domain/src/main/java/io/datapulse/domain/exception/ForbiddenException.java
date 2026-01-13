package io.datapulse.domain.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AppException {

  public ForbiddenException(String messageKey, Object... args) {
    super(HttpStatus.FORBIDDEN, messageKey, args);
  }
}
