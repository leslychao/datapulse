package io.datapulse.core.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpHeaders;

public final class RetryAfterSupport {

  private static final DateTimeFormatter RFC1123_US =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(java.util.Locale.US);

  private RetryAfterSupport() {
  }

  public static Duration parse(HttpHeaders headers, Duration fallback) {
    return parse(headers, Clock.systemUTC(), fallback);
  }

  static Duration parse(HttpHeaders headers, Clock clock, Duration fallback) {
    String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (raw == null) {
      return fallback;
    }
    String v = raw.trim();
    if (v.isEmpty()) {
      return fallback;
    }

    Duration d = parseDeltaSeconds(v);
    if (d == null) {
      d = parseHttpDate(v, clock);
    }

    if (d == null) {
      return fallback;
    }
    return d.isNegative() ? Duration.ZERO : d;
  }

  private static Duration parseDeltaSeconds(String v) {
    // Разрешаем: "+10", "10", "0", "0.1", "10.000"
    if (!v.matches("\\+?\\d+(?:\\.\\d+)?")) {
      return null;
    }
    try {
      if (v.indexOf('.') < 0) {
        String s = (v.charAt(0) == '+') ? v.substring(1) : v;
        return Duration.ofSeconds(Long.parseUnsignedLong(s));
      }
      long ceil = new BigDecimal(v).setScale(0, RoundingMode.CEILING).longValueExact();
      return Duration.ofSeconds(ceil);
    } catch (NumberFormatException | ArithmeticException e) {
      return null;
    }
  }

  private static Duration parseHttpDate(String v, Clock clock) {
    try {
      Instant when = RFC1123_US.parse(v, Instant::from);
      return Duration.between(Instant.now(clock), when);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
