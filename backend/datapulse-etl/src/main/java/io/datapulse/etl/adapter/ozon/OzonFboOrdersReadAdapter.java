package io.datapulse.etl.adapter.ozon;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.adapter.util.OffsetPagedCapture;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OzonFboOrdersReadAdapter {

  private static final String FBO_LIST_PATH = "/v2/posting/fbo/list";
  private static final int PAGE_LIMIT = 1000;

  private final OzonApiCaller apiCaller;
  private final OffsetPagedCapture offsetPagedCapture;

  public List<CaptureResult> captureAllPages(CaptureContext context,
      String clientId, String apiKey,
      OffsetDateTime since, OffsetDateTime to,
      long startOffset) {
    return offsetPagedCapture.captureAllPages(
        context, PAGE_LIMIT,
        OffsetPagedCapture.DEFAULT_MAX_PAGES,
        OzonOffsetPaging.SMALL_PAGE_THRESHOLD_BYTES,
        "Ozon FBO postings", startOffset,
        (currentOffset, pageNumber) -> apiCaller.post(FBO_LIST_PATH,
            Map.of(
                "dir", "ASC",
                "filter", Map.of(
                    "since", since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    "to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                "limit", PAGE_LIMIT,
                "offset", currentOffset,
                "with", Map.of("analytics_data", true, "financial_data", true)),
            context.connectionId(), RateLimitGroup.OZON_DEFAULT,
            clientId, apiKey));
  }
}
