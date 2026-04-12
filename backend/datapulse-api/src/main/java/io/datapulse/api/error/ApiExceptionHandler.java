package io.datapulse.api.error;

import io.datapulse.common.error.ErrorResponse;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(AppException.class)
  public ResponseEntity<ErrorResponse> handleAppException(AppException ex,
      HttpServletRequest request) {
    HttpStatus status = Objects.requireNonNullElse(
        HttpStatus.resolve(ex.getStatusCode()),
        HttpStatus.INTERNAL_SERVER_ERROR);

    log.warn("Application error: messageKey={}, status={}, path={}",
        ex.getMessageKey(), status.value(), request.getRequestURI());

    ErrorResponse response = new ErrorResponse(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        ex.getMessage(),
        ex.getMessageKey(),
        request.getRequestURI(),
        null
    );
    return ResponseEntity.status(status).body(response);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
      HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> new ErrorResponse.FieldError(
            fe.getField(),
            fe.getDefaultMessage(),
            fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null))
        .toList();

    log.warn("Validation failed: path={}, fieldCount={}", request.getRequestURI(),
        fieldErrors.size());

    ErrorResponse response = new ErrorResponse(
        Instant.now(),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        MessageCodes.VALIDATION_FAILED,
        MessageCodes.VALIDATION_FAILED,
        request.getRequestURI(),
        fieldErrors
    );
    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
      HttpServletRequest request) {
    log.warn("Access denied: path={}", request.getRequestURI());

    ErrorResponse response = new ErrorResponse(
        Instant.now(),
        HttpStatus.FORBIDDEN.value(),
        HttpStatus.FORBIDDEN.getReasonPhrase(),
        MessageCodes.ACCESS_DENIED,
        MessageCodes.ACCESS_DENIED,
        request.getRequestURI(),
        null
    );
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    log.warn("Optimistic lock conflict: path={}, entity={}",
        request.getRequestURI(), ex.getPersistentClassName());

    ErrorResponse response = new ErrorResponse(
        Instant.now(),
        HttpStatus.CONFLICT.value(),
        HttpStatus.CONFLICT.getReasonPhrase(),
        MessageCodes.CONCURRENT_MODIFICATION,
        MessageCodes.CONCURRENT_MODIFICATION,
        request.getRequestURI(),
        null
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error: path={}", request.getRequestURI(), ex);

    ErrorResponse response = new ErrorResponse(
        Instant.now(),
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
        MessageCodes.INTERNAL_ERROR,
        MessageCodes.INTERNAL_ERROR,
        request.getRequestURI(),
        null
    );
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}
