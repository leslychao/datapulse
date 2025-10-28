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

  // "+10", "10", "0", "0.1", "10.000"
  private static final Pattern DELTA_SECONDS = Pattern.compile("\\+?\\d+(?:\\.\\d+)?");
  private static final DateTimeFormatter RFC1123_US =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US);

  private RetryAfterSupport() {
  }

  /**
   * Главный вход: парсит Retry-After / X-Ratelimit-Retry / X-Ratelimit-Reset, иначе fallback.
   */
  public static Duration parse(HttpHeaders headers, Duration fallback) {
    return parse(headers, Clock.systemUTC(), fallback);
  }

  /**
   * Перегрузка с Clock (удобно для тестов).
   */
  static Duration parse(HttpHeaders headers, Clock clock, Duration fallback) {
    // 1) Стандартный Retry-After
    String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);

    // 2) WB кастомные хедеры
    String xrRetry = headers.getFirst("X-Ratelimit-Retry");
    String xrReset = headers.getFirst("X-Ratelimit-Reset");
    String xrRemain = headers.getFirst("X-Ratelimit-Remaining");

    // Приоритет:
    //   а) X-Ratelimit-Retry — СКОЛЬКО секунд ждать
    //   б) если Remaining == 0 и есть Reset — ждать до восстановления всплеска
    //   в) Retry-After (RFC1123 дата или дельта)
    //   г) fallback
    Optional<Duration> result =
        firstNonEmpty(xrRetry)
            .flatMap(RetryAfterSupport::parseDeltaSeconds)
            .or(() -> (isZero(xrRemain) && firstNonEmpty(xrReset).isPresent())
                ? parseDeltaSeconds(xrReset)
                : Optional.empty())
            .or(() -> firstNonEmpty(retryAfter)
                .flatMap(v -> parseDeltaSeconds(v)
                    .or(() -> parseHttpDate(v, clock))));

    return result
        .map(RetryAfterSupport::nonNegative)
        .orElse(fallback);
  }

  /* ----------------- helpers ----------------- */

  private static Optional<String> firstNonEmpty(String s) {
    return (s == null || s.trim().isEmpty()) ? Optional.empty() : Optional.of(s.trim());
  }

  private static boolean isZero(String s) {
    return s != null && s.trim().equals("0");
  }

  /**
   * Поддерживает "+10", "10", "0", "0.1", "10.000" (секунды/дробные секунды).
   */
  private static Optional<Duration> parseDeltaSeconds(String v) {
    if (!DELTA_SECONDS.matcher(v).matches()) {
      return Optional.empty();
    }
    try {
      if (v.indexOf('.') < 0) {
        String s = (v.charAt(0) == '+') ? v.substring(1) : v;
        long sec = Long.parseUnsignedLong(s);
        return Optional.of(Duration.ofSeconds(sec));
      } else {
        long ceilSec = new BigDecimal(v).setScale(0, RoundingMode.CEILING).longValueExact();
        return Optional.of(Duration.ofSeconds(ceilSec));
      }
    } catch (NumberFormatException | ArithmeticException e) {
      return Optional.empty();
    }
  }

  /**
   * RFC1123 дата → дельта от now(clock) до неё.
   */
  private static Optional<Duration> parseHttpDate(String v, Clock clock) {
    try {
      Instant when = RFC1123_US.parse(v, Instant::from);
      return Optional.of(Duration.between(Instant.now(clock), when));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /**
   * Никогда не возвращать отрицательные задержки.
   */
  private static Duration nonNegative(Duration d) {
    return (d.isNegative()) ? Duration.ZERO : d;
  }
}
