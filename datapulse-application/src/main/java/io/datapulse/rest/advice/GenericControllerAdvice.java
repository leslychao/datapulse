package io.datapulse.rest.advice;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GenericControllerAdvice {

  private static final Locale LOCALE_EN = Locale.ENGLISH;
  private final MessageSource messageSource;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgNotValid(MethodArgumentNotValidException ex) {
    String msg = buildMessage(ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(msg));
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse> handleBindException(BindException ex) {
    String msg = buildMessage(ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(msg));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    String msg = buildMessage(ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(msg));
  }

  private String buildMessage(Throwable ex) {
    if (ex instanceof MethodArgumentNotValidException manve) {
      return messageFromBindingResult(manve.getBindingResult());
    }
    if (ex instanceof BindException be) {
      return messageFromBindingResult(be);
    }
    if (ex instanceof ConstraintViolationException cve) {
      return messageFromViolations(cve.getConstraintViolations());
    }
    if (ex instanceof IllegalArgumentException iae) {
      String msg = safe(iae.getMessage());
      return msg.isBlank() ? "Некорректный аргумент" : msg;
    }
    return "Ошибка валидации запроса";
  }

  private String messageFromBindingResult(BindingResult br) {
    String joined = br.getAllErrors().stream()
        .map(err -> safe(err.getDefaultMessage()))
        .filter(msg -> !msg.isBlank())
        .distinct()
        .collect(Collectors.joining("; "));

    return joined.isBlank() ? "Ошибка валидации запроса" : joined;
  }

  private String messageFromViolations(Set<ConstraintViolation<?>> violations) {
    String joined = violations.stream()
        .map(v -> safe(v.getMessage()))
        .filter(msg -> !msg.isBlank())
        .distinct()
        .collect(Collectors.joining("; "));

    return joined.isBlank() ? "Ошибка валидации запроса" : joined;
  }

  private String safe(String s) {
    return s == null ? "" : s.trim();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBodyParseError(
      HttpMessageNotReadableException ex,
      Locale locale
  ) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String raw = ex.getMostSpecificCause().getMessage();

    String userMessage = messageSource.getMessage(
        MessageCodes.JSON_PARSE_BODY_INVALID,
        new Object[]{raw},
        "Некорректное тело запроса",
        locale
    );

    return ResponseEntity.status(status).body(new ErrorResponse(userMessage));
  }

  @ExceptionHandler
  public ResponseEntity<ErrorResponse> handleException(Throwable ex, Locale locale) {
    HttpStatus status = resolveStatus(ex);
    String message = resolveMessage(ex, locale);
    String logMessage = resolveMessage(ex, LOCALE_EN);
    log.error(logMessage, ex);
    return ResponseEntity.status(status).body(new ErrorResponse(message));
  }

  private HttpStatus resolveStatus(Throwable ex) {
    if (ex instanceof AppException appEx && appEx.getStatus() != null) {
      return appEx.getStatus();
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private String resolveMessage(Throwable ex, Locale locale) {
    if (ex instanceof AppException appEx) {
      String key = appEx.getMessageKey();
      Object[] args = appEx.getArgs();
      if (key != null) {
        return messageSource.getMessage(key, args, key, locale);
      }
    }
    return ex.getMessage() != null ? ex.getMessage() : ex.toString();
  }

  public record ErrorResponse(String message) {

  }
}
