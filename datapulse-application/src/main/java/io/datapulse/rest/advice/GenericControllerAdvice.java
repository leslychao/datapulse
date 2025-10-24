package io.datapulse.rest.advice;

import io.datapulse.core.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GenericControllerAdvice {

  @ExceptionHandler
  public ResponseEntity<Object> handleGlobalException(Throwable throwable) {
    return ResponseEntity
        .status(getHttpStatus(throwable))
        .body(new ErrorResponse(throwable.getMessage()));
  }

  public static HttpStatus getHttpStatus(Throwable throwable) {
    HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    if (throwable instanceof NotFoundException) {
      httpStatus = HttpStatus.NOT_FOUND;
    }
    return httpStatus;
  }

  record ErrorResponse(String message) {

  }
}
