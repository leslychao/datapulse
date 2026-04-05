package io.datapulse.etl.adapter.ozon;

import java.util.List;
import java.util.Map;

import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OzonStocksReadAdapter {

    private static final String STOCKS_PATH = "/v4/product/info/stocks";
    private static final int PAGE_LIMIT = 1000;

    private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
            new JsonPathCursorExtractor("result.last_id");

    private static final OzonLastIdPagedListCapture.OzonLastIdListSpec SPEC =
            new OzonLastIdPagedListCapture.OzonLastIdListSpec(
                    STOCKS_PATH,
                    PAGE_LIMIT,
                    CURSOR_EXTRACTOR,
                    "stocks",
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
                OzonLastIdPagedListCapture.NO_OP);
    }
}
