package io.datapulse.core.client;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

public final class RetryAfterSupport {

  private RetryAfterSupport() {
  }

  public static Duration parse(HttpHeaders headers, Clock clock, Duration fallback) {
    String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (raw == null) {
      return fallback;
    }

    String v = raw.trim();
    if (v.isEmpty()) {
      return fallback;
    }

    return parseDeltaSeconds(v)
        .or(() -> parseHttpDate(v, clock))
        .map(RetryAfterSupport::nonNegative)
        .orElse(fallback);
  }

  private static Optional<Duration> parseDeltaSeconds(String v) {
    if (!v.matches("^[+]?\\d+(?:\\.\\d+)?$")) {
      return Optional.empty();
    }
    try {
      if (v.indexOf('.') < 0) {
        long secs = Long.parseLong(v.startsWith("+") ? v.substring(1) : v);
        return Optional.of(Duration.ofSeconds(secs));
      }
      double d = Double.parseDouble(v);
      long secs = (long) Math.ceil(d);
      return Optional.of(Duration.ofSeconds(secs));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  private static Optional<Duration> parseHttpDate(String v, Clock clock) {
    try {
      ZonedDateTime when = ZonedDateTime.parse(
          v, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US));
      return Optional.of(Duration.between(clock.instant(), when.toInstant()));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  private static Duration nonNegative(Duration d) {
    return d.isNegative() ? Duration.ZERO : d;
  }
}
