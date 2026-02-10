package io.datapulse.rest.advice;

import io.datapulse.core.i18n.I18nMessageService;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.SecurityException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GenericControllerAdvice {

  private final I18nMessageService i18nMessageService;

  @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
  public ResponseEntity<ErrorResponse> handleAccessDenied(Throwable ex, Locale locale) {
    return handleException(SecurityException.accessDenied(), locale);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException ex,
      Locale locale) {
    return handleException(SecurityException.unauthenticatedJwtNotFound(), locale);
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      BindException.class,
      ConstraintViolationException.class
  })
  public ResponseEntity<ErrorResponse> handleValidationExceptions(Exception ex) {
    String message = validationMessage(ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException ex,
      Locale locale
  ) {
    String parameterName = normalize(ex.getName());
    String requiredType = ex.getRequiredType() != null
        ? ex.getRequiredType().getSimpleName()
        : "unknown";

    return badRequest(
        MessageCodes.REQUEST_PARAM_TYPE_MISMATCH,
        locale,
        parameterName,
        requiredType
    );
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
      MissingServletRequestParameterException ex,
      Locale locale
  ) {
    return badRequest(MessageCodes.REQUEST_PARAM_MISSING, locale, normalize(ex.getParameterName()));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> handleMissingRequestPart(
      MissingServletRequestPartException ex,
      Locale locale
  ) {
    return badRequest(MessageCodes.REQUEST_PART_MISSING, locale,
        normalize(ex.getRequestPartName()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBodyParseError(HttpMessageNotReadableException ex,
      Locale locale) {
    Throwable cause = ex.getMostSpecificCause();
    String message = i18nMessageService.userMessage(cause, locale);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(Locale locale) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(new ErrorResponse(i18n(MessageCodes.REQUEST_METHOD_NOT_SUPPORTED, locale)));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(Locale locale) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(new ErrorResponse(i18n(MessageCodes.REQUEST_CONTENT_TYPE_NOT_SUPPORTED, locale)));
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoHandlerFound(Locale locale) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(i18n(MessageCodes.RESOURCE_NOT_FOUND, locale)));
  }

  @ExceptionHandler
  public ResponseEntity<ErrorResponse> handleException(Throwable ex, Locale locale) {
    HttpStatus status = resolveStatus(ex);

    String userMessage = i18nMessageService.userMessage(ex, locale);
    String logMessage = i18nMessageService.userMessage(ex, Locale.ENGLISH);

    if (status.is5xxServerError()) {
      log.error(logMessage, ex);
    } else {
      log.warn(logMessage);
    }

    return ResponseEntity.status(status).body(new ErrorResponse(userMessage));
  }

  private ResponseEntity<ErrorResponse> badRequest(String messageCode, Locale locale,
      Object... args) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(i18n(messageCode, locale, args)));
  }

  private String i18n(String messageCode, Locale locale, Object... args) {
    return i18nMessageService.userMessage(messageCode, locale, args);
  }

  private HttpStatus resolveStatus(Throwable ex) {
    if (ex instanceof AppException appException && appException.getStatus() != null) {
      return appException.getStatus();
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private String validationMessage(Exception ex) {
    if (ex instanceof MethodArgumentNotValidException manve) {
      return fromBindingResult(manve.getBindingResult());
    }
    if (ex instanceof BindException bindException) {
      return fromBindingResult(bindException);
    }
    if (ex instanceof ConstraintViolationException cve) {
      return fromViolations(cve.getConstraintViolations());
    }
    return i18nMessageService.userMessage(
        new AppException(HttpStatus.BAD_REQUEST, MessageCodes.VALIDATION_ERROR),
        Locale.getDefault());
  }

  private String fromBindingResult(BindingResult bindingResult) {
    String joined = bindingResult.getAllErrors().stream()
        .map(error -> normalize(error.getDefaultMessage()))
        .filter(message -> !message.isBlank())
        .distinct()
        .collect(Collectors.joining("; "));
    return joined.isBlank() ? i18nMessageService.userMessage(
        new AppException(HttpStatus.BAD_REQUEST, MessageCodes.VALIDATION_ERROR),
        Locale.getDefault()) : joined;
  }

  private String fromViolations(Set<ConstraintViolation<?>> violations) {
    String joined = violations.stream()
        .map(violation -> normalize(violation.getMessage()))
        .filter(message -> !message.isBlank())
        .distinct()
        .collect(Collectors.joining("; "));
    return joined.isBlank() ? i18nMessageService.userMessage(
        new AppException(HttpStatus.BAD_REQUEST, MessageCodes.VALIDATION_ERROR),
        Locale.getDefault()) : joined;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  public record ErrorResponse(String message) {

  }
}
