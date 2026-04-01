package io.datapulse.etl.adapter.ozon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
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
public class OzonPromoReadAdapter {

    private static final String ACTIONS_PATH = "/v1/actions";
    private static final String ACTION_PRODUCTS_PATH = "/v1/actions/products";
    private static final String ACTION_CANDIDATES_PATH = "/v1/actions/candidates";
    private static final int PAGE_LIMIT = 1000;

    private final OzonApiCaller apiCaller;
    private final StreamingPageCapture pageCapture;

    public List<CaptureResult> captureActions(CaptureContext context,
                                              String clientId, String apiKey) {
        Flux<DataBuffer> body = apiCaller.get(
                ACTIONS_PATH,
                context.connectionId(), RateLimitGroup.OZON_PROMO,
                clientId, apiKey);

        PageCaptureResult page = pageCapture.capture(
                body, context, 0, NoCursorExtractor.INSTANCE);

        log.info("Ozon actions capture completed: connectionId={}, byteSize={}",
                context.connectionId(), page.captureResult().byteSize());
        return List.of(page.captureResult());
    }

    public List<CaptureResult> captureActionProducts(CaptureContext context,
                                                     String clientId, String apiKey,
                                                     long actionId) {
        List<CaptureResult> results = new ArrayList<>();
        int offset = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            Map<String, Object> body = Map.of(
                    "action_id", actionId,
                    "offset", offset,
                    "limit", PAGE_LIMIT);

            Flux<DataBuffer> response = apiCaller.post(
                    ACTION_PRODUCTS_PATH, body,
                    context.connectionId(), RateLimitGroup.OZON_PROMO,
                    clientId, apiKey);

            PageCaptureResult page = pageCapture.capture(
                    response, context, pageNumber, NoCursorExtractor.INSTANCE);
            results.add(page.captureResult());

            offset += PAGE_LIMIT;
            pageNumber++;
            hasMore = page.captureResult().byteSize() > 500;

            log.debug("Ozon action products page captured: connectionId={}, actionId={}, page={}",
                    context.connectionId(), actionId, pageNumber);
        }
        return results;
    }

    public List<CaptureResult> captureActionCandidates(CaptureContext context,
                                                       String clientId, String apiKey,
                                                       long actionId) {
        List<CaptureResult> results = new ArrayList<>();
        int offset = 0;
        int pageNumber = 0;
        boolean hasMore = true;

        while (hasMore) {
            Map<String, Object> body = Map.of(
                    "action_id", actionId,
                    "offset", offset,
                    "limit", PAGE_LIMIT);

            Flux<DataBuffer> response = apiCaller.post(
                    ACTION_CANDIDATES_PATH, body,
                    context.connectionId(), RateLimitGroup.OZON_PROMO,
                    clientId, apiKey);

            PageCaptureResult page = pageCapture.capture(
                    response, context, pageNumber, NoCursorExtractor.INSTANCE);
            results.add(page.captureResult());

            offset += PAGE_LIMIT;
            pageNumber++;
            hasMore = page.captureResult().byteSize() > 500;

            log.debug("Ozon action candidates page captured: connectionId={}, actionId={}, page={}",
                    context.connectionId(), actionId, pageNumber);
        }
        return results;
    }
}
