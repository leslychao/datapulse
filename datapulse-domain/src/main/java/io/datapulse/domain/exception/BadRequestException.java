package io.datapulse.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BadRequestException extends AppException {

  public BadRequestException(String messageKey, Object... args) {
    super(HttpStatus.BAD_REQUEST, messageKey, args);
  }
}
