package io.datapulse.etl.adapter.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonProductInfoReadAdapter {

    private static final String PRODUCT_INFO_PATH = "/v3/product/info/list";
    private static final int BATCH_SIZE = 1000;

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllBatches(CaptureContext context,
                                                 String clientId, String apiKey,
                                                 List<Long> productIds) {
        List<CaptureResult> results = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i += BATCH_SIZE) {
            List<Long> batch = productIds.subList(i, Math.min(i + BATCH_SIZE, productIds.size()));
            int pageNumber = i / BATCH_SIZE;

            Flux<DataBuffer> body = apiCaller.post(PRODUCT_INFO_PATH,
                    Map.of("product_id", batch),
                    context.connectionId(), RateLimitGroup.OZON_DEFAULT,
                    clientId, apiKey);

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
