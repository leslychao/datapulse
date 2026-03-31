package io.datapulse.etl.adapter.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonAttributesReadAdapter {

    private static final String BASE_URL = "https://api-seller.ozon.ru";
    private static final String ATTRIBUTES_PATH = "/v4/product/info/attributes";
    private static final int PAGE_LIMIT = 1000;

    private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
            new JsonPathCursorExtractor("last_id");

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context,
                                               String clientId, String apiKey,
                                               List<Long> productIds) {
        List<CaptureResult> results = new ArrayList<>();
        String lastId = "";
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            rateLimiter.acquire(context.connectionId(), RateLimitGroup.OZON_DEFAULT).join();

            String currentLastId = lastId;
            Flux<DataBuffer> body = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + ATTRIBUTES_PATH)
                    .header("Client-Id", clientId)
                    .header("Api-Key", apiKey)
                    .bodyValue(Map.of(
                            "filter", Map.of("product_id", productIds, "visibility", "ALL"),
                            "last_id", currentLastId,
                            "limit", PAGE_LIMIT,
                            "sort_dir", "ASC"))
                    .exchangeToFlux(response -> {
                        int status = response.statusCode().value();
                        rateLimiter.onResponse(context.connectionId(), RateLimitGroup.OZON_DEFAULT, status);
                        if (response.statusCode().isError()) {
                            return response.createException().flatMapMany(Flux::error);
                        }
                        return response.bodyToFlux(DataBuffer.class);
                    });

            PageCaptureResult page = pageCapture.capture(body, context, pageNumber, CURSOR_EXTRACTOR);
            results.add(page.captureResult());

            lastId = page.cursor();
            if (lastId == null || lastId.isEmpty() || page.captureResult().byteSize() < 200) {
                hasMore = false;
            }

            pageNumber++;
            log.debug("Ozon attributes page captured: connectionId={}, page={}",
                    context.connectionId(), pageNumber);
        }

        log.info("Ozon attributes capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
