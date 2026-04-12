package io.datapulse.etl.adapter.yandex;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.CursorPagedCapture;
import io.datapulse.etl.adapter.util.CursorPagedCapture.CursorPageSpec;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.cursor.JsonPathCursorExtractor;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CRITICAL (F-3): Uses {@code POST /v1/businesses/{businessId}/orders} (business-level).
 * Legacy {@code GET /v2/campaigns/{id}/orders} is DEPRECATED and must NOT be used.
 * <p>
 * Max 30-day date range per request — adapter splits wider ranges into 30-day windows.
 * Max page size: 50 (Yandex hard limit).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YandexOrdersReadAdapter {

  private static final String ORDERS_PATH = "/v1/businesses/%d/orders";
  private static final int PAGE_LIMIT = 50;
  private static final int MAX_DAYS_PER_REQUEST = 30;

  private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
      new JsonPathCursorExtractor("result.paging.nextPageToken");

  private final YandexApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context, String apiKey, long businessId,
      LocalDate dateFrom, LocalDate dateTo) {
    List<CaptureResult> allResults = new ArrayList<>();
    String basePath = ORDERS_PATH.formatted(businessId);

    List<DateWindow> windows = splitIntoWindows(dateFrom, dateTo);

    for (DateWindow window : windows) {
      log.info("Yandex orders: capturing window {}-{}, connectionId={}",
          window.from(), window.to(), context.connectionId());

      Map<String, Object> requestBody = buildRequestBody(window);

      CursorPageSpec spec = new CursorPageSpec(
          CURSOR_EXTRACTOR,
          "Yandex orders (%s to %s)".formatted(window.from(), window.to()),
          (cursor, pageNumber) -> {
            String path = buildPagePath(basePath, cursor);
            return apiCaller.post(
                path, context.connectionId(), RateLimitGroup.YANDEX_ORDERS,
                apiKey, requestBody);
          });

      List<CaptureResult> windowResults = cursorPagedCapture.captureAllPages(
          context, null, spec, CursorPagedCapture.NO_OP);
      allResults.addAll(windowResults);

      log.info("Yandex orders: window {}-{} captured, pages={}",
          window.from(), window.to(), windowResults.size());
    }

    log.info("Yandex orders capture completed: connectionId={}, windows={}, totalPages={}",
        context.connectionId(), windows.size(), allResults.size());
    return allResults;
  }

  static List<DateWindow> splitIntoWindows(LocalDate dateFrom, LocalDate dateTo) {
    List<DateWindow> windows = new ArrayList<>();
    LocalDate windowStart = dateFrom;

    while (!windowStart.isAfter(dateTo)) {
      LocalDate windowEnd = windowStart.plusDays(MAX_DAYS_PER_REQUEST - 1);
      if (windowEnd.isAfter(dateTo)) {
        windowEnd = dateTo;
      }
      windows.add(new DateWindow(windowStart, windowEnd));
      windowStart = windowEnd.plusDays(1);
    }

    return windows;
  }

  private Map<String, Object> buildRequestBody(DateWindow window) {
    return Map.of(
        "dates", Map.of(
            "creationDateFrom", window.from().toString(),
            "creationDateTo", window.to().toString()),
        "fake", false);
  }

  private String buildPagePath(String basePath, String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return basePath + "?limit=" + PAGE_LIMIT;
    }
    return basePath + "?limit=" + PAGE_LIMIT + "&pageToken=" + cursor;
  }

  record DateWindow(LocalDate from, LocalDate to) {}
}
