package io.datapulse.etl.adapter.wb;

import io.datapulse.etl.adapter.util.HttpRetryClassifier;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import io.datapulse.integration.logging.MarketplaceHttpRequestLogger;
import java.net.URI;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class WbApiCaller {

    private static final String MARKETPLACE = "WB";

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final MarketplaceHttpRequestLogger httpRequestLogger;

    public Flux<DataBuffer> get(String url, String apiToken,
                                long connectionId, RateLimitGroup group) {
        return Flux.defer(() -> {
            rateLimiter.acquire(connectionId, group).join();
            URI uri = URI.create(url);
            httpRequestLogger.logRequest(
                MARKETPLACE, HttpMethod.GET, uri, connectionId, group, null);
            return webClientBuilder.build()
                    .get()
                    .uri(uri)
                    .header("Authorization", apiToken)
                    .exchangeToFlux(response -> handleResponse(response, connectionId, group));
        }).retryWhen(HttpRetryClassifier.retrySpec());
    }

    public Flux<DataBuffer> get(String uriTemplate, Function<UriBuilder, URI> uriFunction,
                                String apiToken,
                                long connectionId, RateLimitGroup group) {
        return Flux.defer(() -> {
            rateLimiter.acquire(connectionId, group).join();
            URI uri = uriFunction.apply(UriComponentsBuilder.fromUriString(uriTemplate));
            httpRequestLogger.logRequest(
                MARKETPLACE, HttpMethod.GET, uri, connectionId, group, null);
            return webClientBuilder.build()
                    .get()
                    .uri(uri)
                    .header("Authorization", apiToken)
                    .exchangeToFlux(response -> handleResponse(response, connectionId, group));
        }).retryWhen(HttpRetryClassifier.retrySpec());
    }

    public Flux<DataBuffer> post(String url, Object body, String apiToken,
                                 long connectionId, RateLimitGroup group) {
        return Flux.defer(() -> {
            rateLimiter.acquire(connectionId, group).join();
            URI uri = URI.create(url);
            httpRequestLogger.logRequest(
                MARKETPLACE, HttpMethod.POST, uri, connectionId, group, body);
            return webClientBuilder.build()
                    .post()
                    .uri(uri)
                    .header("Authorization", apiToken)
                    .bodyValue(body)
                    .exchangeToFlux(response -> handleResponse(response, connectionId, group));
        }).retryWhen(HttpRetryClassifier.retrySpec());
    }

    private Flux<DataBuffer> handleResponse(ClientResponse response,
                                            long connectionId, RateLimitGroup group) {
        int status = response.statusCode().value();
        rateLimiter.onResponse(connectionId, group, status);
        if (status == 204) {
            return Flux.empty();
        }
        if (response.statusCode().isError()) {
            return response.createException().flatMapMany(Flux::error);
        }
        return response.bodyToFlux(DataBuffer.class);
    }
}
