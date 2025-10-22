package ru.vkim.datapulse.etl.client;

import reactor.core.publisher.Mono;

public interface MarketplaceClient {
    Mono<String> fetchSalesJson(String shopId, String token, String fromIso, String toIso);
    Mono<String> fetchStocksJson(String shopId, String token);
    Mono<String> fetchFinanceJson(String shopId, String token);
    Mono<String> fetchReviewsJson(String shopId, String token);
    Mono<String> fetchAdsJson(String shopId, String token);
}
