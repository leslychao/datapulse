package io.datapulse.etl.i18n;

import io.datapulse.domain.exception.AppException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExceptionMessageService {

  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("ru");

  private final MessageSource messageSource;

  public HttpStatus resolveStatus(Throwable throwable) {
    if (throwable instanceof AppException appException) {
      HttpStatus status = appException.getStatus();
      if (status != null) {
        return status;
      }
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  public String resolveUserMessage(Throwable throwable, Locale locale) {
    Locale targetLocale = locale != null ? locale : DEFAULT_LOCALE;

    if (throwable instanceof AppException appException) {
      String messageKey = appException.getMessageKey();
      if (messageKey != null && !messageKey.isBlank()) {
        Object[] args = appException.getArgs();
        return messageSource.getMessage(messageKey, args, messageKey, targetLocale);
      }
    }

    String message = throwable.getMessage();
    if (message != null && !message.isBlank()) {
      return message;
    }

    return throwable.toString();
  }

  public String resolveLogMessage(Throwable throwable) {
    return resolveUserMessage(throwable, DEFAULT_LOCALE);
  }

  public void logEtlError(Throwable throwable) {
    log.error("ETL error: {}", resolveLogMessage(throwable));
  }
}
