package io.datapulse.core.i18n;

import static io.datapulse.domain.MessageCodes.ERROR_REASON;
import static io.datapulse.domain.MessageCodes.ERROR_REASON_FALLBACK;
import static io.datapulse.domain.MessageCodes.ERROR_UNKNOWN;

import io.datapulse.domain.exception.AppException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class I18nMessageService {

  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("ru");
  private static final int MAX_REASON_LENGTH = 400;

  private final MessageSource messageSource;

  public String userMessage(Throwable throwable, Locale locale) {
    Locale targetLocale = targetLocale(locale);

    if (throwable == null) {
      return message(ERROR_UNKNOWN, targetLocale);
    }

    return baseMessage(throwable, targetLocale);
  }

  public String userMessage(Throwable throwable) {
    return userMessage(throwable, DEFAULT_LOCALE);
  }

  public String userMessage(String code, Object... args) {
    return userMessage(code, DEFAULT_LOCALE, args);
  }

  public String userMessage(String code, Locale locale, Object... args) {
    Locale targetLocale = targetLocale(locale);
    return message(code, args, targetLocale, code);
  }

  public String logMessage(Throwable throwable, Locale locale) {
    Locale targetLocale = targetLocale(locale);

    if (throwable == null) {
      return message(ERROR_UNKNOWN, targetLocale);
    }

    String baseMessage = baseMessage(throwable, targetLocale);

    Throwable rootCause = rootCause(throwable);
    if (rootCause == null) {
      return baseMessage;
    }

    String reason = message(
        ERROR_REASON,
        new Object[]{causeMessage(rootCause)},
        targetLocale,
        message(ERROR_REASON_FALLBACK, targetLocale)
    );

    return baseMessage + ", " + reason;
  }

  public String logMessage(Throwable throwable) {
    return logMessage(throwable, DEFAULT_LOCALE);
  }

  private Throwable rootCause(Throwable throwable) {
    if (throwable instanceof AppException appException) {
      Throwable root = ExceptionUtils.getRootCause(appException);
      return root != null ? root : appException;
    }

    Throwable root = ExceptionUtils.getRootCause(throwable);
    return root != null ? root : throwable;
  }

  private String baseMessage(Throwable throwable, Locale locale) {
    if (throwable instanceof AppException appException) {
      String key = appException.getMessageKey();
      Object[] args = appException.getArgs();

      if (hasText(key)) {
        return message(key, args, locale, key);
      }

      return message(ERROR_UNKNOWN, locale);
    }

    return message(ERROR_UNKNOWN, locale);
  }

  private String causeMessage(Throwable cause) {
    String raw = normalize(cause.getMessage());
    if (raw.isBlank()) {
      raw = normalize(cause.toString());
    }
    if (raw.length() <= MAX_REASON_LENGTH) {
      return raw;
    }
    return raw.substring(0, MAX_REASON_LENGTH).trim() + "...";
  }

  private String message(String code, Locale locale) {
    return message(code, null, locale, code);
  }

  private String message(String code, Object[] args, Locale locale, String defaultMessage) {
    if (!hasText(code)) {
      return defaultMessage;
    }
    return messageSource.getMessage(code, args, defaultMessage, locale);
  }

  private Locale targetLocale(Locale locale) {
    return locale != null ? locale : DEFAULT_LOCALE;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
