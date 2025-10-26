package io.datapulse.core.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;

public final class RetryAfterSupport {

  private static final Pattern DELTA_SECONDS_PATTERN = Pattern.compile("^[+]?\\d+(?:\\.\\d+)?$");
  private static final DateTimeFormatter HTTP_DATE_FORMATTER =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US);

  private RetryAfterSupport() {
  }

  public static Duration parse(HttpHeaders headers, Clock clock, Duration fallback) {
    return Optional.ofNullable(headers.getFirst(HttpHeaders.RETRY_AFTER))
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .flatMap(v -> parseDeltaSeconds(v)
            .or(() -> parseHttpDate(v, clock))
            .map(RetryAfterSupport::nonNegative))
        .orElse(fallback);
  }

  private static Optional<Duration> parseDeltaSeconds(String headerValue) {
    if (!DELTA_SECONDS_PATTERN.matcher(headerValue).matches()) {
      return Optional.empty();
    }

    try {
      if (headerValue.indexOf('.') < 0) {
        String numericPart = headerValue.charAt(0) == '+' ? headerValue.substring(1) : headerValue;
        long seconds = Long.parseUnsignedLong(numericPart);
        return Optional.of(Duration.ofSeconds(seconds));
      }

      BigDecimal numericValue = new BigDecimal(headerValue);
      long roundedSeconds = numericValue.setScale(0, RoundingMode.CEILING).longValueExact();
      return Optional.of(Duration.ofSeconds(roundedSeconds));

    } catch (NumberFormatException | ArithmeticException parseError) {
      return Optional.empty();
    }
  }

  private static Optional<Duration> parseHttpDate(String headerValue, Clock systemClock) {
    try {
      ZonedDateTime httpDateTime = ZonedDateTime.parse(headerValue, HTTP_DATE_FORMATTER);
      Duration durationUntilTarget = Duration.between(systemClock.instant(),
          httpDateTime.toInstant());
      return Optional.of(durationUntilTarget);
    } catch (DateTimeParseException parseError) {
      return Optional.empty();
    }
  }

  private static Duration nonNegative(Duration d) {
    return d.isNegative() ? Duration.ZERO : d;
  }
}
