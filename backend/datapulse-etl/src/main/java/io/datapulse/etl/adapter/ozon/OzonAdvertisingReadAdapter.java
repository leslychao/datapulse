package io.datapulse.etl.adapter.ozon;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import io.datapulse.etl.adapter.util.HttpRetryClassifier;
import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import io.datapulse.integration.logging.MarketplaceHttpRequestLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

/**
 * HTTP adapter for Ozon Performance API (advertising campaigns and statistics).
 * Uses Bearer token auth from {@link OzonPerformanceTokenService} instead of
 * the Client-Id/Api-Key auth used by {@link OzonApiCaller}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OzonAdvertisingReadAdapter {

  private static final String CAMPAIGNS_PATH = "/api/client/campaign";
  private static final String STATS_PATH = "/api/client/statistics/campaign/product";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;
  private final MarketplaceHttpRequestLogger httpRequestLogger;
  private final StreamingPageCapture pageCapture;

  /**
   * Fetches all advertising campaigns and captures the response to S3.
   */
  public List<CaptureResult> captureCampaigns(CaptureContext context,
      String accessToken) {
    Flux<DataBuffer> body = performanceGet(
        CAMPAIGNS_PATH, accessToken, context.connectionId());

    PageCaptureResult page = pageCapture.capture(
        body, context, 0, NoCursorExtractor.INSTANCE);

    log.info("Ozon advertising campaigns captured: connectionId={}, byteSize={}",
        context.connectionId(), page.captureResult().byteSize());
    return List.of(page.captureResult());
  }

  /**
   * Fetches SKU-level advertising statistics for the given campaigns and date range,
   * captures the response to S3.
   */
  public List<CaptureResult> captureStatistics(CaptureContext context,
      String accessToken, List<Long> campaignIds,
      LocalDate dateFrom, LocalDate dateTo) {
    if (campaignIds.isEmpty()) {
      log.info("No campaigns to fetch statistics for: connectionId={}",
          context.connectionId());
      return List.of();
    }

    String campaignsParam = campaignIds.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(","));

    String baseUrl = properties.getOzon().getPerformanceBaseUrl();
    URI uri = UriComponentsBuilder.fromUriString(baseUrl + STATS_PATH)
        .queryParam("campaigns", campaignsParam)
        .queryParam("dateFrom", dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .queryParam("dateTo", dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .build()
        .toUri();

    Flux<DataBuffer> body = Flux.defer(() -> {
      rateLimiter.acquire(context.connectionId(), RateLimitGroup.OZON_PERFORMANCE).join();
      httpRequestLogger.logRequest(
          "OZON_PERF", HttpMethod.GET, uri, context.connectionId(),
          RateLimitGroup.OZON_PERFORMANCE, null);
      return webClientBuilder.build()
          .get()
          .uri(uri)
          .header("Authorization", "Bearer " + accessToken)
          .exchangeToFlux(response ->
              handleResponse(response, context.connectionId()));
    }).retryWhen(HttpRetryClassifier.retrySpec());

    PageCaptureResult page = pageCapture.capture(
        body, context, 0, NoCursorExtractor.INSTANCE);

    log.info("Ozon advertising statistics captured: connectionId={}, "
            + "campaigns={}, dateRange=[{}, {}], byteSize={}",
        context.connectionId(), campaignIds.size(), dateFrom, dateTo,
        page.captureResult().byteSize());
    return List.of(page.captureResult());
  }

  private Flux<DataBuffer> performanceGet(String path, String accessToken,
      long connectionId) {
    return Flux.defer(() -> {
      rateLimiter.acquire(connectionId, RateLimitGroup.OZON_PERFORMANCE).join();
      String baseUrl = properties.getOzon().getPerformanceBaseUrl();
      URI uri = URI.create(baseUrl + path);
      httpRequestLogger.logRequest(
          "OZON_PERF", HttpMethod.GET, uri, connectionId,
          RateLimitGroup.OZON_PERFORMANCE, null);
      return webClientBuilder.build()
          .get()
          .uri(uri)
          .header("Authorization", "Bearer " + accessToken)
          .exchangeToFlux(response -> handleResponse(response, connectionId));
    }).retryWhen(HttpRetryClassifier.retrySpec());
  }

  private Flux<DataBuffer> handleResponse(ClientResponse response, long connectionId) {
    int status = response.statusCode().value();
    rateLimiter.onResponse(connectionId, RateLimitGroup.OZON_PERFORMANCE, status);
    if (status == 204) {
      return Flux.empty();
    }
    if (response.statusCode().isError()) {
      return response.createException().flatMapMany(Flux::error);
    }
    return response.bodyToFlux(DataBuffer.class);
  }
}
