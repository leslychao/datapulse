package io.datapulse.marketplaces.resilience;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Вспомогательные методы для расчёта задержек (экспонента, кап, джиттер).
 */
final class BackoffSupport {

  private BackoffSupport() {
  }

  /**
   * Экспоненциальный backoff с «ceil»-капом и add-джиттером. exp = min(cap, base * 2^(attempt-1)) +
   * rand(0..jitter)
   *
   * @param retries число уже выполненных повторов (0 на первой ошибке)
   */
  static Duration expBackoff(long retries, Duration base, Duration cap, Duration jitter) {
    long attempt = Math.max(1, retries + 1);
    long factor = attempt >= 63 ? Long.MAX_VALUE : (1L << (attempt - 1));

    Duration exp;
    try {
      exp = base.multipliedBy(factor);
    } catch (ArithmeticException e) {
      exp = Duration.ofMillis(Long.MAX_VALUE);
    }

    if (exp.compareTo(cap) > 0) {
      exp = cap;
    }
    if (jitter == null || jitter.isZero() || jitter.isNegative()) {
      return exp;
    }

    long bound = Math.max(0, jitter.toMillis());
    long rand = (bound == 0) ? 0 : ThreadLocalRandom.current().nextLong(bound + 1);

    try {
      Duration withJitter = exp.plusMillis(rand);
      return withJitter.compareTo(cap) > 0 ? cap : withJitter;
    } catch (ArithmeticException e) {
      return cap;
    }
  }

  /**
   * Добавляет джиттер к базовой задержке и применяет верхний кап.
   */
  static Duration addJitterAndCap(Duration baseDelay, Duration jitter, Duration cap) {
    if (jitter == null || jitter.isZero() || jitter.isNegative()) {
      return baseDelay.compareTo(cap) > 0 ? cap : baseDelay;
    }
    long bound = Math.max(0, jitter.toMillis());
    long rand = (bound == 0) ? 0 : ThreadLocalRandom.current().nextLong(bound + 1);
    Duration withJitter;
    try {
      withJitter = baseDelay.plusMillis(rand);
    } catch (ArithmeticException e) {
      withJitter = cap;
    }
    return withJitter.compareTo(cap) > 0 ? cap : withJitter;
  }
}
