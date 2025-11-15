package io.datapulse.marketplaces.resilience;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

abstract class BaseRetryPolicy {

  protected static final double JITTER_FACTOR = 0.20;

  protected static final int STATUS_TOO_MANY_REQUESTS = 429;
  protected static final int STATUS_SERVICE_UNAVAILABLE = 503;
  protected static final int STATUS_REQUEST_TIMEOUT = 408;
  protected static final int STATUS_CONFLICT = 409;
  protected static final int STATUS_TOO_EARLY = 425;

  protected static final String HDR_X_RETRY = "X-Ratelimit-Retry";

  protected static boolean isRetryableStatus(int s) {
    return s == STATUS_TOO_MANY_REQUESTS
        || s == STATUS_REQUEST_TIMEOUT
        || s == STATUS_CONFLICT
        || s == STATUS_TOO_EARLY
        || (s >= 500 && s <= 599);
  }

  protected static Duration parseRetryAfter(HttpHeaders headers) {
    return parseSeconds(headers.getFirst(HttpHeaders.RETRY_AFTER));
  }

  protected static Duration parseSeconds(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      long seconds = Long.parseLong(value.trim());
      return seconds < 0 ? null : Duration.ofSeconds(seconds);
    } catch (NumberFormatException ignore) {
      return null;
    }
  }

  protected static Duration expBackoff(long retries, Duration base, Duration cap) {
    long attempt = Math.max(1, retries + 1);
    long raw = base.toMillis() * (1L << Math.min(attempt - 1, 4)); // рост до x16
    long capped = Math.min(raw, cap.toMillis());
    long jitter = Math.round(capped * JITTER_FACTOR * Math.random());
    long total = Math.min(cap.toMillis(), capped + jitter);
    return Duration.ofMillis(total);
  }

  protected boolean isTransient(Throwable t) {
    if (t instanceof WebClientResponseException ex) {
      int s = ex.getStatusCode().value();
      return s == 429
          || s == 408
          || s == 425
          || (s >= 500 && s <= 599);
    }
    return t instanceof TimeoutException;
  }
}
