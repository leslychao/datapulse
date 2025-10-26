package io.datapulse.rest.advice;

import static io.datapulse.domain.MessageCodes.VALIDATION_REQUEST_INVALID;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GenericControllerAdvice {

  private final MessageSource messageSource;
  private static final Locale LOCALE_EN = Locale.ENGLISH;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
      Locale locale) {
    var fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
    String userMsg;

    if (fieldError != null) {
      userMsg = messageSource.getMessage(fieldError, locale);
    } else {
      userMsg = messageSource.getMessage(VALIDATION_REQUEST_INVALID, null,
          "Некорректные данные запроса", locale);
    }

    String logMsg = messageSource.getMessage(VALIDATION_REQUEST_INVALID, null,
        "Invalid request body", LOCALE_EN);
    log.warn("{}: {}", logMsg, ex.getBindingResult().getAllErrors());

    return ResponseEntity.badRequest().body(new ErrorResponse(userMsg));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBodyParseError(
      HttpMessageNotReadableException ex,
      Locale locale
  ) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String raw = ex.getMostSpecificCause().getMessage();

    String userMessage = messageSource.getMessage(
        MessageCodes.JSON_BODY_INVALID,
        new Object[]{raw},
        "Некорректное тело запроса",
        locale
    );

    String logMessage = messageSource.getMessage(
        MessageCodes.JSON_BODY_INVALID,
        new Object[]{raw},
        "Invalid request body",
        LOCALE_EN
    );

    log.warn(logMessage, ex);
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
