package io.datapulse.etl.adapter.wb;

import java.net.URI;
import java.util.function.Function;

import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class WbApiCaller {

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;

    public Flux<DataBuffer> get(String url, String apiToken,
                                long connectionId, RateLimitGroup group) {
        rateLimiter.acquire(connectionId, group).join();
        return webClientBuilder.build()
                .get()
                .uri(url)
                .header("Authorization", apiToken)
                .exchangeToFlux(response -> handleResponse(response, connectionId, group));
    }

    public Flux<DataBuffer> get(String uriTemplate, Function<UriBuilder, URI> uriFunction,
                                String apiToken,
                                long connectionId, RateLimitGroup group) {
        rateLimiter.acquire(connectionId, group).join();
        return webClientBuilder.build()
                .get()
                .uri(uriTemplate, uriFunction)
                .header("Authorization", apiToken)
                .exchangeToFlux(response -> handleResponse(response, connectionId, group));
    }

    public Flux<DataBuffer> post(String url, Object body, String apiToken,
                                 long connectionId, RateLimitGroup group) {
        rateLimiter.acquire(connectionId, group).join();
        return webClientBuilder.build()
                .post()
                .uri(url)
                .header("Authorization", apiToken)
                .bodyValue(body)
                .exchangeToFlux(response -> handleResponse(response, connectionId, group));
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
