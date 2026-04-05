package io.datapulse.etl.adapter.ozon;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.StreamingPageCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Ozon Finance adapter with automatic date-window splitting.
 * The {@code /v3/finance/transaction/list} endpoint rejects date ranges exceeding 1 month,
 * so we split into 28-day windows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OzonFinanceReadAdapter {

  private static final String FINANCE_PATH = "/v3/finance/transaction/list";
  private static final int PAGE_SIZE = 1000;
  private static final int MAX_WINDOW_DAYS = 28;

  private static final JsonPathCursorExtractor PAGE_COUNT_EXTRACTOR =
      new JsonPathCursorExtractor("result.page_count");

  private final OzonApiCaller apiCaller;
  private final StreamingPageCapture pageCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context,
      String clientId, String apiKey,
      OffsetDateTime dateFrom, OffsetDateTime dateTo,
      int startPageInclusive) {
    List<OzonDateWindows.Window> windows =
        OzonDateWindows.split(dateFrom, dateTo, MAX_WINDOW_DAYS);

    if (windows.size() == 1) {
      return captureSingleWindow(context, clientId, apiKey,
          windows.get(0), startPageInclusive, 1, 1);
    }

    List<CaptureResult> allResults = new ArrayList<>();
    for (int i = 0; i < windows.size(); i++) {
      int windowStartPage = i == 0 ? startPageInclusive : 1;
      CaptureContext windowCtx = i == 0
          ? context
          : CaptureContextFactory.withNewRequestId(context);
      allResults.addAll(captureSingleWindow(windowCtx, clientId, apiKey,
          windows.get(i), windowStartPage, i + 1, windows.size()));
    }

    log.info("Ozon finance capture completed: connectionId={}, windows={}, totalPages={}",
        context.connectionId(), windows.size(), allResults.size());
    return allResults;
  }

  private List<CaptureResult> captureSingleWindow(CaptureContext context,
      String clientId, String apiKey,
      OzonDateWindows.Window window, int startPageInclusive,
      int windowIndex, int totalWindows) {
    List<CaptureResult> results = new ArrayList<>();
    int page = Math.max(1, startPageInclusive);
    int totalPages = Integer.MAX_VALUE;
    int pageNumber = 0;

    while (page <= totalPages) {
      int currentPage = page;
      Flux<DataBuffer> body = apiCaller.post(FINANCE_PATH,
          Map.of(
              "filter", Map.of(
                  "date", Map.of(
                      "from", window.since()
                          .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                      "to", window.to()
                          .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                  "operation_type", List.of(),
                  "posting_number", "",
                  "transaction_type", "all"),
              "page", currentPage,
              "page_size", PAGE_SIZE),
          context.connectionId(), RateLimitGroup.OZON_DEFAULT,
          clientId, apiKey);

      PageCaptureResult captured = pageCapture.capture(
          body, context, pageNumber, PAGE_COUNT_EXTRACTOR, null,
          String.valueOf(currentPage));
      results.add(captured.captureResult());

      String pageCountStr = captured.cursor();
      if (pageCountStr != null && !pageCountStr.isEmpty()) {
        totalPages = Integer.parseInt(pageCountStr);
      }

      page++;
      pageNumber++;
      log.debug("Ozon finance page captured: connectionId={}, window={}/{}, "
              + "page={}/{}, byteSize={}",
          context.connectionId(), windowIndex, totalWindows,
          page - 1, totalPages, captured.captureResult().byteSize());
    }

    log.info("Ozon finance window {}/{} completed: connectionId={}, pages={}",
        windowIndex, totalWindows, context.connectionId(), results.size());
    return results;
  }
}
