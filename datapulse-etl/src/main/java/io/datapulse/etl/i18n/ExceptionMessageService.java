package io.datapulse.etl.i18n;

import io.datapulse.domain.exception.AppException;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExceptionMessageService {

  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("ru");

  private final MessageSource messageSource;

  public String userMessage(Throwable throwable, Locale locale) {
    if (throwable == null) {
      return message("error.unknown", null, locale, "Неизвестная ошибка");
    }

    Locale target = locale != null ? locale : DEFAULT_LOCALE;

    if (throwable instanceof AppException appException) {
      String key = appException.getMessageKey();
      Object[] args = appException.getArgs();
      if (hasText(key)) {
        return message(key, args, target, key);
      }
    }

    String msg = throwable.getMessage();
    if (hasText(msg)) {
      return msg;
    }

    return throwable.toString();
  }

  public String userMessage(Throwable throwable) {
    return userMessage(throwable, DEFAULT_LOCALE);
  }

  public String userMessage(String code, Object... args) {
    return message(code, args, DEFAULT_LOCALE, code);
  }

  public String logMessage(Throwable throwable) {
    return userMessage(Objects.requireNonNull(throwable), DEFAULT_LOCALE);
  }

  public String logMessage(String code, Object... args) {
    return message(code, args, DEFAULT_LOCALE, code);
  }

  public void logError(Throwable throwable) {
    if (throwable == null) {
      log.error("ETL error: {}", message("error.unknown"));
      return;
    }
    log.error("ETL error: {}", logMessage(throwable), throwable);
  }

  public void logError(String code, Object... args) {
    log.error("ETL error: {}", logMessage(code, args));
  }

  private String message(String key, Object[] args, Locale locale, String defaultMsg) {
    if (!hasText(key)) {
      return defaultMsg;
    }
    Locale target = locale != null ? locale : DEFAULT_LOCALE;
    return messageSource.getMessage(key, args, defaultMsg, target);
  }

  private String message(String key) {
    return message(key, null, DEFAULT_LOCALE, key);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
