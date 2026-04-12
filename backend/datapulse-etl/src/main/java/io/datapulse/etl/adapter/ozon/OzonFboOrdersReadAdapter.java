package io.datapulse.etl.adapter.ozon;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.OffsetPagedCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ozon FBO orders adapter with automatic date-window splitting.
 * Large date ranges are split into 60-day windows to avoid timeouts
 * and oversized responses from {@code /v2/posting/fbo/list}.
 */
@Service
@RequiredArgsConstructor
public class OzonFboOrdersReadAdapter {

  private static final String FBO_LIST_PATH = "/v2/posting/fbo/list";
  private static final int PAGE_LIMIT = 1000;
  private static final int MAX_WINDOW_DAYS = 60;

  private final OzonApiCaller apiCaller;
  private final OffsetPagedCapture offsetPagedCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context,
      String clientId, String apiKey,
      OffsetDateTime since, OffsetDateTime to,
      long startOffset) {
    List<OzonDateWindows.Window> windows = OzonDateWindows.split(since, to, MAX_WINDOW_DAYS);

    if (windows.size() == 1) {
      return captureSingleWindow(context, clientId, apiKey,
          windows.get(0), startOffset, 1, 1);
    }

    List<CaptureResult> allResults = new ArrayList<>();
    for (int i = 0; i < windows.size(); i++) {
      long windowOffset = i == 0 ? startOffset : 0L;
      CaptureContext windowCtx = i == 0
          ? context
          : CaptureContextFactory.withNewRequestId(context);
      allResults.addAll(captureSingleWindow(windowCtx, clientId, apiKey,
          windows.get(i), windowOffset, i + 1, windows.size()));
    }
    return allResults;
  }

  private List<CaptureResult> captureSingleWindow(CaptureContext context,
      String clientId, String apiKey,
      OzonDateWindows.Window window, long startOffset,
      int windowIndex, int totalWindows) {
    return offsetPagedCapture.captureAllPages(
        context, PAGE_LIMIT,
        OffsetPagedCapture.DEFAULT_MAX_PAGES,
        OzonOffsetPaging.SMALL_PAGE_THRESHOLD_BYTES,
        "Ozon FBO postings (window %d/%d)".formatted(windowIndex, totalWindows),
        startOffset,
        (currentOffset, pageNumber) -> apiCaller.post(FBO_LIST_PATH,
            Map.of(
                "dir", "ASC",
                "filter", Map.of(
                    "since", window.since().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    "to", window.to().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                "limit", PAGE_LIMIT,
                "offset", currentOffset,
                "with", Map.of("analytics_data", true, "financial_data", true)),
            context.connectionId(), RateLimitGroup.OZON_DEFAULT,
            clientId, apiKey));
  }
}
