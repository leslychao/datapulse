package io.datapulse.etl.i18n;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.dto.EtlSnapshotContext;
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

  public String resolveUserMessage(Throwable throwable, Locale locale) {
    if (throwable == null) {
      return "Неизвестная ошибка";
    }

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
    return resolveUserMessage(Objects.requireNonNull(throwable), DEFAULT_LOCALE);
  }

  public void logEtlError(Throwable throwable) {
    if (throwable == null) {
      log.error("ETL error: неизвестная ошибка (throwable is null)");
      return;
    }
    String message = resolveLogMessage(throwable);
    log.error("ETL error: {}", message, throwable);
  }

  public void logSnapshotError(
      Throwable throwable,
      EtlSnapshotContext context,
      String stage
  ) {
    if (throwable == null) {
      log.error(
          "ETL snapshot error: неизвестная ошибка, stage={}, context={}",
          stage,
          context
      );
      return;
    }

    String message = resolveLogMessage(throwable);

    log.error("""
            ETL snapshot error: {}
              stage={}
              requestId={}
              accountId={}
              event={}
              marketplace={}
              sourceId={}
              snapshotId={}
              snapshotFile={}
            """,
        message,
        stage,
        context.requestId(),
        context.accountId(),
        context.event(),
        context.marketplace(),
        context.sourceId(),
        context.snapshotId(),
        context.snapshotFile(),
        throwable
    );
  }
}
