package io.datapulse.marketplaces.resilience;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;

/**
 * Минимальный и достаточный парсер задержек: Приоритет: X-RateLimit-Retry → X-RateLimit-Reset /
 * RateLimit-Reset / RateLimit-Reset-After → Retry-After.
 */
public final class RetryAfterSupport {

  private static final String XR_RETRY = "X-Ratelimit-Retry";
  private static final String XR_RESET = "X-Ratelimit-Reset";
  private static final String RL_RESET = "RateLimit-Reset";
  private static final String RL_RESET_AFTER = "RateLimit-Reset-After";

  private RetryAfterSupport() {
  }

  public static Duration parse(HttpHeaders headers, Duration fallback) {
    return parse(headers, Clock.systemUTC(), fallback);
  }

  static Duration parse(HttpHeaders h, Clock clock, Duration fallback) {
    Optional<Duration> candidate = Stream.<Optional<Duration>>of(
            header(h, XR_RETRY).flatMap(RetryAfterSupport::parseDeltaSeconds),

            // reset: сначала как дельта, иначе epoch s/ms
            header(h, XR_RESET)
                .or(() -> header(h, RL_RESET))
                .or(() -> header(h, RL_RESET_AFTER))
                .flatMap(v -> parseReset(v, clock)),

            // Retry-After: либо дельта, либо RFC1123
            header(h, HttpHeaders.RETRY_AFTER).flatMap(v ->
                parseDeltaSeconds(v).or(() -> parseHttpDate(v, clock)))
        )
        .flatMap(Optional::stream)
        .findFirst();

    return candidate.map(RetryAfterSupport::nonNegative).orElse(fallback);
  }

  /* -------- helpers -------- */

  private static Optional<String> header(HttpHeaders h, String name) {
    String v = h.getFirst(name);
    if (v == null && name.startsWith("X-Ratelimit-")) {
      v = h.getFirst(name.replace("Ratelimit", "RateLimit")); // доп. регистр/вариант
    }
    return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v.trim());
  }

  /**
   * "+10", "0.4" → ceil секунд; отрицательные → empty.
   */
  private static Optional<Duration> parseDeltaSeconds(String v) {
    try {
      String s = v.charAt(0) == '+' ? v.substring(1) : v;
      BigDecimal bd = new BigDecimal(s);
      if (bd.signum() < 0) {
        return Optional.empty();
      }
      long ceilSec = bd.setScale(0, RoundingMode.CEILING).longValueExact();
      return Optional.of(Duration.ofSeconds(ceilSec));
    } catch (RuntimeException ignore) {
      return Optional.empty();
    }
  }

  /**
   * Reset: пробуем как дельту, иначе epoch (len>=12 → millis, иначе seconds).
   */
  private static Optional<Duration> parseReset(String v, Clock clock) {
    var delta = parseDeltaSeconds(v);
    if (delta.isPresent()) {
      return delta;
    }
    try {
      String s = v.trim();
      long n = Long.parseLong(s);
      Instant when = (s.length() >= 12) ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
      return Optional.of(Duration.between(Instant.now(clock), when));
    } catch (RuntimeException ignore) {
      return Optional.empty();
    }
  }

  private static Optional<Duration> parseHttpDate(String v, Clock clock) {
    try {
      ZonedDateTime when = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
      return Optional.of(Duration.between(Instant.now(clock), when.toInstant()));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private static Duration nonNegative(Duration d) {
    return d.isNegative() ? Duration.ZERO : d;
  }
}
