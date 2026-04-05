package io.datapulse.etl.adapter.ozon;

import java.util.List;
import java.util.Map;

import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonProductListReadAdapter {

    private static final String PRODUCT_LIST_PATH = "/v3/product/list";
    private static final int PAGE_LIMIT = 1000;

    private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
            new JsonPathCursorExtractor("result.last_id");

    private static final OzonLastIdPagedListCapture.OzonLastIdListSpec SPEC =
            new OzonLastIdPagedListCapture.OzonLastIdListSpec(
                    PRODUCT_LIST_PATH,
                    PAGE_LIMIT,
                    CURSOR_EXTRACTOR,
                    "product list",
                    currentLastId ->
                            Map.of(
                                    "filter", Map.of("visibility", "ALL"),
                                    "last_id", currentLastId,
                                    "limit", PAGE_LIMIT));

    private final OzonLastIdPagedListCapture lastIdPagedCapture;

    public List<CaptureResult> captureAllPages(
            CaptureContext context, String clientId, String apiKey, String initialLastId) {
        return lastIdPagedCapture.captureAllPages(
                context,
                clientId,
                apiKey,
                initialLastId,
                SPEC,
                (pageIndex, ctx, outcome) -> {
                    if (outcome.stop() && outcome.nonAdvancingCursor()) {
                        log.debug(
                                "Ozon product list pagination stopped: non-advancing last_id,"
                                    + " connectionId={}",
                                ctx.connectionId());
                    }
                    log.debug(
                            "Ozon product list page captured: connectionId={}, page={}",
                            ctx.connectionId(),
                            pageIndex + 1);
                });
    }
}
