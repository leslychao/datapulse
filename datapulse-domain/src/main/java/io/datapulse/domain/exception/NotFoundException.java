package io.datapulse.domain.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends AppException {

  public NotFoundException(String messageKey, Object... args) {
    super(HttpStatus.NOT_FOUND, messageKey, args);
  }
}
