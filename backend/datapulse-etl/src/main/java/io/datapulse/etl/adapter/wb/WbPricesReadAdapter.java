package io.datapulse.etl.adapter.wb;

import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.NoCursorExtractor;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbPricesReadAdapter {

    private static final String PRICES_PATH = "/api/v2/list/goods/filter";
    private static final int PAGE_LIMIT = 1000;

    private final WbApiCaller apiCaller;
    private final IntegrationProperties properties;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureAllPages(CaptureContext context, String apiToken) {
        List<CaptureResult> results = new ArrayList<>();
        int offset = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        String baseUrl = properties.getWildberries().getPricesBaseUrl();

        while (hasMore) {
            int currentOffset = offset;
            Flux<DataBuffer> body = apiCaller.get(
                    baseUrl + PRICES_PATH,
                    uriBuilder -> uriBuilder
                            .queryParam("limit", PAGE_LIMIT)
                            .queryParam("offset", currentOffset)
                            .build(),
                    apiToken, context.connectionId(), RateLimitGroup.WB_PRICES_READ);

            PageCaptureResult page = pageCapture.capture(
                    body, context, pageNumber, NoCursorExtractor.INSTANCE);
            results.add(page.captureResult());

            offset += PAGE_LIMIT;
            pageNumber++;

            if (page.captureResult().byteSize() < 500) {
                hasMore = false;
            }

            log.debug("WB prices page captured: connectionId={}, page={}, byteSize={}",
                    context.connectionId(), pageNumber, page.captureResult().byteSize());
        }

        log.info("WB prices capture completed: connectionId={}, totalPages={}",
                context.connectionId(), results.size());
        return results;
    }
}
