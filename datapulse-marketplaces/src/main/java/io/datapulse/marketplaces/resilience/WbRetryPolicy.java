package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties.Resilience;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
public class WbRetryPolicy extends BaseRetryPolicy implements MarketplaceRetryPolicy {

  @Override
  public Retry retryFor(MarketplaceType marketplace, EndpointKey endpoint, Resilience cfg) {
    final int maxAttempts = cfg.getMaxAttempts();
    final var base = cfg.getBaseBackoff();
    final var cap = cfg.getMaxBackoff();
    final var fallback = cfg.getRetryAfterFallback();

    return Retry.from(signals -> signals.flatMap(rs -> {
      long attempt = rs.totalRetries() + 1;
      Throwable error = rs.failure();

      if (rs.totalRetries() >= maxAttempts - 1) {
        log.warn("[{}:{}] retry exhausted after {} attempts; cause={}",
            marketplace, endpoint, maxAttempts, error.getClass().getSimpleName());
        return Mono.error(error);
      }

      if (error instanceof WebClientResponseException ex) {
        int status = ex.getStatusCode().value();
        HttpHeaders headers = ex.getHeaders();

        // WB-спец ветка раньше общей: 429/503 → X-Ratelimit-Retry → Retry-After → fallback
        if (status == STATUS_TOO_MANY_REQUESTS || status == STATUS_SERVICE_UNAVAILABLE) {
          var xr = parseSeconds(headers.getFirst(HDR_X_RETRY));
          if (xr != null) {
            log.info("[WB:{}] retry #{} in {} (status={}, hdr=x-retry)",
                endpoint, attempt, xr, status);
            return Mono.delay(xr);
          }
          var ra = parseRetryAfter(headers);
          if (ra != null && !ra.isNegative()) {
            log.info("[WB:{}] retry #{} in {} (status={}, hdr=retry-after)",
                endpoint, attempt, ra, status);
            return Mono.delay(ra);
          }
          var fb = (fallback != null) ? fallback : base;
          log.info("[WB:{}] retry #{} in {} (status={}, hdr=none, fallback)",
              endpoint, attempt, fb, status);
          return Mono.delay(fb);
        }

        // Общая ретраимая группа
        if (isRetryableStatus(status)) {
          var ra = parseRetryAfter(headers);
          var delay = (ra != null && !ra.isNegative())
              ? ra
              : expBackoff(rs.totalRetries(), base, cap);
          log.info("[WB:{}] retry #{} in {} (status={}, retry-after={})",
              endpoint, attempt, delay, status, ra);
          return Mono.delay(delay);
        }

        return Mono.error(error);
      }

      if (error instanceof WebClientRequestException reqEx && isTransient(reqEx)) {
        var delay = expBackoff(rs.totalRetries(), base, cap);
        log.info("[WB:{}] net-transient → retry #{} in {}", endpoint, attempt, delay);
        return Mono.delay(delay);
      }

      return Mono.error(error);
    }));
  }
}
