package io.datapulse.etl.adapter.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
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
public class OzonProductInfoReadAdapter {

    private static final String BASE_URL = "https://api-seller.ozon.ru";
    private static final String PRODUCT_INFO_PATH = "/v3/product/info/list";
    private static final int BATCH_SIZE = 1000;

    private final WebClient.Builder webClientBuilder;
    private final MarketplaceRateLimiter rateLimiter;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllBatches(CaptureContext context,
                                                 String clientId, String apiKey,
                                                 List<Long> productIds) {
        List<CaptureResult> results = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i += BATCH_SIZE) {
            List<Long> batch = productIds.subList(i, Math.min(i + BATCH_SIZE, productIds.size()));
            int pageNumber = i / BATCH_SIZE;

            rateLimiter.acquire(context.connectionId(), RateLimitGroup.OZON_DEFAULT).join();

            Flux<DataBuffer> body = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + PRODUCT_INFO_PATH)
                    .header("Client-Id", clientId)
                    .header("Api-Key", apiKey)
                    .bodyValue(Map.of("product_id", batch))
                    .exchangeToFlux(response -> {
                        int status = response.statusCode().value();
                        rateLimiter.onResponse(context.connectionId(), RateLimitGroup.OZON_DEFAULT, status);
                        if (response.statusCode().isError()) {
                            return response.createException().flatMapMany(Flux::error);
                        }
                        return response.bodyToFlux(DataBuffer.class);
                    });

            var page = pageCapture.capture(body, context, pageNumber, NoCursorExtractor.INSTANCE);
            results.add(page.captureResult());

            log.debug("Ozon product info batch captured: connectionId={}, batchSize={}, page={}",
                    context.connectionId(), batch.size(), pageNumber);
        }

        log.info("Ozon product info capture completed: connectionId={}, totalBatches={}",
                context.connectionId(), results.size());
        return results;
    }
}
