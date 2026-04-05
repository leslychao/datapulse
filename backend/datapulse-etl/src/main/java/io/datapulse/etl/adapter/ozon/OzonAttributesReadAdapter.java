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
public class OzonAttributesReadAdapter {

    private static final String ATTRIBUTES_PATH = "/v4/product/info/attributes";
    private static final int PAGE_LIMIT = 1000;

    private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
            new JsonPathCursorExtractor("last_id");

    private final OzonLastIdPagedListCapture lastIdPagedCapture;

    public List<CaptureResult> captureAllPages(
            CaptureContext context,
            String clientId,
            String apiKey,
            List<Long> productIds,
            String initialLastId) {
        OzonLastIdPagedListCapture.OzonLastIdListSpec spec =
                new OzonLastIdPagedListCapture.OzonLastIdListSpec(
                        ATTRIBUTES_PATH,
                        PAGE_LIMIT,
                        CURSOR_EXTRACTOR,
                        "attributes",
                        currentLastId ->
                                Map.of(
                                        "filter",
                                        Map.of("product_id", productIds, "visibility", "ALL"),
                                        "last_id", currentLastId,
                                        "limit", PAGE_LIMIT,
                                        "sort_dir", "ASC"));

        return lastIdPagedCapture.captureAllPages(
                context,
                clientId,
                apiKey,
                initialLastId,
                spec,
                (pageIndex, ctx, outcome) ->
                        log.debug(
                                "Ozon attributes page captured: connectionId={}, page={}",
                                ctx.connectionId(),
                                pageIndex + 1));
    }
}
