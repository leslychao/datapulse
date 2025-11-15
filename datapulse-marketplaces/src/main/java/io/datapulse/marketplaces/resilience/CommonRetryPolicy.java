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
public class CommonRetryPolicy extends BaseRetryPolicy implements MarketplaceRetryPolicy {

  @Override
  public Retry retryFor(MarketplaceType marketplace, EndpointKey endpoint, Resilience cfg) {
    final int maxAttempts = cfg.getMaxAttempts();
    final var base = cfg.getBaseBackoff();
    final var cap = cfg.getMaxBackoff();

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

        if (isRetryableStatus(status)) {
          var headerDelay = parseRetryAfter(headers);
          var delay = (headerDelay != null && !headerDelay.isNegative())
              ? headerDelay
              : expBackoff(rs.totalRetries(), base, cap);

          log.info("[{}:{}] retry #{} in {} (status={}, retry-after={})",
              marketplace, endpoint, attempt, delay, status, headerDelay);
          return Mono.delay(delay);
        }
        return Mono.error(error);
      }

      if (error instanceof WebClientRequestException reqEx && isTransientNetwork(reqEx)) {
        var delay = expBackoff(rs.totalRetries(), base, cap);
        log.info("[{}:{}] net-transient → retry #{} in {}", marketplace, endpoint, attempt, delay);
        return Mono.delay(delay);
      }

      return Mono.error(error);
    }));
  }
}
