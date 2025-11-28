package io.datapulse.core.i18n;

import static io.datapulse.domain.MessageCodes.ERROR_REASON;
import static io.datapulse.domain.MessageCodes.ERROR_UNKNOWN;

import io.datapulse.domain.exception.AppException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class I18nMessageService {

  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("ru");

  private final MessageSource messageSource;

  public String userMessage(Throwable throwable, Locale locale) {
    if (throwable == null) {
      return message(ERROR_UNKNOWN);
    }

    Locale target = locale != null ? locale : DEFAULT_LOCALE;

    if (throwable instanceof AppException appException) {
      String key = appException.getMessageKey();
      Object[] args = appException.getArgs();

      String baseMessage = hasText(key)
          ? message(key, args, target, key)
          : throwable.toString();

      Throwable cause = appException.getCause();
      if (cause != null) {
        String reasonMessage = message(
            ERROR_REASON,
            new Object[]{cause.toString()},
            target,
            "Причина: " + cause
        );
        return baseMessage + ", " + reasonMessage;
      }

      return baseMessage;
    }

    return throwable.toString();
  }

  public String userMessage(Throwable throwable) {
    return userMessage(throwable, DEFAULT_LOCALE);
  }

  public String userMessage(String code, Object... args) {
    return message(code, args, DEFAULT_LOCALE, code);
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
