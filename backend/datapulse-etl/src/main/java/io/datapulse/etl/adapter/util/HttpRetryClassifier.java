package io.datapulse.etl.adapter.util;

import java.net.ConnectException;
import java.time.Duration;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

/**
 * Shared HTTP retry policy for marketplace API callers.
 *
 * <p>Retryable: HTTP 420 (Yandex rate limit), 429, 500, 502, 503, 504, connection timeout,
 * read timeout. Not retryable: other 4xx, parsing errors, credential errors (401/403).
 */
@Slf4j
public final class HttpRetryClassifier {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
    private static final double JITTER = 0.5;

    private HttpRetryClassifier() {}

    public static boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientResponseException wce) {
            int status = wce.getStatusCode().value();
            return status == 420 || status == 429 || status >= 500;
        }
        return ex instanceof ConnectTimeoutException
                || ex instanceof ReadTimeoutException
                || ex instanceof ConnectException;
    }

    public static Retry retrySpec() {
        return Retry.backoff(MAX_ATTEMPTS, INITIAL_BACKOFF)
                .maxBackoff(MAX_BACKOFF)
                .jitter(JITTER)
                .filter(HttpRetryClassifier::isRetryable)
                .doBeforeRetry(signal -> log.warn(
                        "HTTP retry attempt {}/{}: {}",
                        signal.totalRetries() + 1, MAX_ATTEMPTS,
                        signal.failure().getMessage()));
    }
}
