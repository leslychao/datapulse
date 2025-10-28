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
 * Универсальный парсер задержек для повторной попытки по HTTP-заголовкам.
 * Поддерживаемые варианты:
 * - Retry-After: delta-seconds или HTTP-date (RFC 7231)
 * - X-Ratelimit-Retry: delta-seconds
 * - X-Ratelimit-Remaining == 0 + X-Ratelimit-Reset: delta-seconds / epoch-seconds / epoch-millis
 * Также допускаем "X-RateLimit-*" регистр/вариант (WB и пр.).
 */
public final class RetryAfterSupport {

  private static final String XR_RETRY = "X-Ratelimit-Retry";
  private static final String XR_RESET = "X-Ratelimit-Reset";
  private static final String XR_REMAIN = "X-Ratelimit-Remaining";

  private RetryAfterSupport() {
  }

  /** Главный вход: парсит Retry-After / X-Ratelimit-Retry / X-Ratelimit-Reset, иначе fallback. */
  public static Duration parse(HttpHeaders headers, Duration fallback) {
    return parse(headers, Clock.systemUTC(), fallback);
  }

  /** Перегрузка с Clock (удобно для тестов). */
  static Duration parse(HttpHeaders h, Clock clock, Duration fallback) {
    Optional<Duration> candidate =
        Stream.<Optional<Duration>>of(
                header(h, XR_RETRY).flatMap(RetryAfterSupport::parseDeltaSeconds),

                isZero(h.getFirst(XR_REMAIN))
                    ? header(h, XR_RESET).flatMap(v -> parseReset(v, clock))
                    : Optional.empty(),

                header(h, HttpHeaders.RETRY_AFTER).flatMap(v ->
                    parseDeltaSeconds(v).or(() -> parseHttpDate(v, clock))
                )
            )
            .flatMap(Optional::stream)
            .findFirst();

    return candidate.map(RetryAfterSupport::nonNegative).orElse(fallback);
  }

  /* ----------------- helpers ----------------- */

  // Также пытаемся X-RateLimit-* (другой регистр/вариант).
  private static Optional<String> header(HttpHeaders h, String name) {
    String v = h.getFirst(name);
    if (v == null) {
      v = h.getFirst(name.replace("Ratelimit", "RateLimit"));
    }
    return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v.trim());
  }

  /** Ноль как число: "0", "00", "0.0" → true. */
  private static boolean isZero(String v) {
    if (v == null || v.isBlank()) return false;
    try {
      return new BigDecimal(v.trim()).compareTo(BigDecimal.ZERO) == 0;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  /**
   * "+10", "10", "0", "0.1", "10.000" → Duration (округление вверх до секунд).
   * BigDecimal — без потерь точности.
   */
  private static Optional<Duration> parseDeltaSeconds(String v) {
    try {
      String s = (v.charAt(0) == '+') ? v.substring(1) : v;
      long ceilSec = new BigDecimal(s).setScale(0, RoundingMode.CEILING).longValueExact();
      if (ceilSec < 0L) return Optional.empty();
      return Optional.of(Duration.ofSeconds(ceilSec));
    } catch (RuntimeException ignore) {
      return Optional.empty();
    }
  }

  /**
   * X-Ratelimit-Reset: пробуем как дельту → иначе как epoch (секунды/миллисекунды).
   * Эвристика: длина >= 12 символов → epoch millis, иначе epoch seconds.
   */
  private static Optional<Duration> parseReset(String v, Clock clock) {
    Optional<Duration> delta = parseDeltaSeconds(v);
    if (delta.isPresent()) return delta;

    try {
      String s = v.trim();
      long n = Long.parseLong(s);
      Instant when = (s.length() >= 12) ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
      return Optional.of(Duration.between(Instant.now(clock), when));
    } catch (RuntimeException ignore) {
      return Optional.empty();
    }
  }

  /** Retry-After как RFC1123-дата. */
  private static Optional<Duration> parseHttpDate(String v, Clock clock) {
    try {
      ZonedDateTime when = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
      return Optional.of(Duration.between(Instant.now(clock), when.toInstant()));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /** Гарантируем неотрицательность задержки. */
  private static Duration nonNegative(Duration d) {
    return d.isNegative() ? Duration.ZERO : d;
  }
}
