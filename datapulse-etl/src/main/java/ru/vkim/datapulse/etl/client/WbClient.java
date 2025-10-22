package ru.vkim.datapulse.etl.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WbClient implements MarketplaceClient {

    private final WebClient web;

    @Value("${datapulse.marketplaces.wb.base-url}")
    private String baseUrl;
    @Value("${datapulse.marketplaces.wb.endpoints.sales}")
    private String salesPath;
    @Value("${datapulse.marketplaces.wb.endpoints.stocks}")
    private String stocksPath;
    @Value("${datapulse.marketplaces.wb.endpoints.finance}")
    private String financePath;
    @Value("${datapulse.marketplaces.wb.endpoints.reviews}")
    private String reviewsPath;
    @Value("${datapulse.marketplaces.wb.endpoints.ads}")
    private String adsPath;
    @Value("${datapulse.marketplaces.wb.headers.auth-name}")
    private String authHeaderName;

    @Override
    @Retry(name = "wbClient")
    @CircuitBreaker(name = "wbClient")
    public Mono<String> fetchSalesJson(String shopId, String token, String fromIso, String toIso) {
        return web.get()
                .uri(baseUrl + salesPath + "?dateFrom={from}&dateTo={to}", fromIso, toIso)
                .header(authHeaderName, token)
                .retrieve()
                .bodyToMono(String.class);
    }

    @Override @Retry(name = "wbClient") @CircuitBreaker(name = "wbClient")
    public Mono<String> fetchStocksJson(String shopId, String token) {
        return web.get()
                .uri(baseUrl + stocksPath)
                .header(authHeaderName, token)
                .retrieve()
                .bodyToMono(String.class);
    }

    @Override @Retry(name = "wbClient") @CircuitBreaker(name = "wbClient")
    public Mono<String> fetchFinanceJson(String shopId, String token) {
        return web.get()
                .uri(baseUrl + financePath)
                .header(authHeaderName, token)
                .retrieve()
                .bodyToMono(String.class);
    }

    @Override @Retry(name = "wbClient") @CircuitBreaker(name = "wbClient")
    public Mono<String> fetchReviewsJson(String shopId, String token) {
        return web.get()
                .uri(baseUrl + reviewsPath)
                .header(authHeaderName, token)
                .retrieve()
                .bodyToMono(String.class);
    }

    @Override @Retry(name = "wbClient") @CircuitBreaker(name = "wbClient")
    public Mono<String> fetchAdsJson(String shopId, String token) {
        return web.get()
                .uri(baseUrl + adsPath)
                .header(authHeaderName, token)
                .retrieve()
                .bodyToMono(String.class);
    }
}
