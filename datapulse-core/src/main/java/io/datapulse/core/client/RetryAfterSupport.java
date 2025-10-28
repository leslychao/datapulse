package io.datapulse.core.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;

public final class RetryAfterSupport {

  private static final Pattern DELTA_SECONDS = Pattern.compile("^[+]?\\d+(?:\\.\\d+)?$");
  private static final DateTimeFormatter RFC1123_US =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US);

  private RetryAfterSupport() {
  }

  public static Duration parse(HttpHeaders headers, Clock clock, Duration fallback) {
    String headerValue = headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (headerValue == null) {
      return fallback;
    }

    String value = headerValue.trim();
    if (value.isEmpty()) {
      return fallback;
    }

    return parseDeltaSeconds(value)
        .or(() -> parseHttpDateToInstant(value).map(t -> Duration.between(clock.instant(), t)))
        .map(RetryAfterSupport::nonNegative)
        .orElse(fallback);
  }

  private static Optional<Duration> parseDeltaSeconds(String v) {
    if (!DELTA_SECONDS.matcher(v).matches()) {
      return Optional.empty();
    }
    try {
      int dot = v.indexOf('.');
      if (dot < 0) {
        String s = (v.charAt(0) == '+') ? v.substring(1) : v;
        long seconds = Long.parseUnsignedLong(s);
        return Optional.of(Duration.ofSeconds(seconds));
      }
      long seconds = new BigDecimal(v).setScale(0, RoundingMode.CEILING).longValueExact();
      return Optional.of(Duration.ofSeconds(seconds));
    } catch (NumberFormatException | ArithmeticException e) {
      return Optional.empty();
    }
  }

  private static Optional<Instant> parseHttpDateToInstant(String v) {
    try {
      return Optional.of(RFC1123_US.parse(v, Instant::from));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private static Duration nonNegative(Duration d) {
    return d.isNegative() ? Duration.ZERO : d;
  }
}
