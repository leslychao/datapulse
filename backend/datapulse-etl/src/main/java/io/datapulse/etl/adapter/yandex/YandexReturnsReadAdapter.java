package io.datapulse.etl.adapter.yandex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 * Returns are campaign-level: endpoint {@code GET /v2/campaigns/{campaignId}/returns}.
 * Adapter fans out across all discovered campaigns for a connection.
 * <p>
 * NOTE: docs URL for returns returned 404 during verification — contract reconstructed
 * from API probing and general docs structure. May need adjustment with real account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YandexReturnsReadAdapter {

  private static final String RETURNS_PATH = "/v2/campaigns/%d/returns";
  private static final int PAGE_LIMIT = 50;

  private static final JsonPathCursorExtractor CURSOR_EXTRACTOR =
      new JsonPathCursorExtractor("result.paging.nextPageToken");

  private final YandexApiCaller apiCaller;
  private final CursorPagedCapture cursorPagedCapture;

  public List<CaptureResult> captureAllPages(
      CaptureContext context, String apiKey,
      List<Long> campaignIds, LocalDate fromDate, LocalDate toDate) {
    List<CaptureResult> allResults = new ArrayList<>();

    for (long campaignId : campaignIds) {
      log.info("Yandex returns: starting capture for campaignId={}, connectionId={}",
          campaignId, context.connectionId());

      String basePath = RETURNS_PATH.formatted(campaignId);

      CursorPageSpec spec = new CursorPageSpec(
          CURSOR_EXTRACTOR,
          "Yandex returns (campaign=%d)".formatted(campaignId),
          (cursor, pageNumber) -> {
            String path = buildPagePath(basePath, cursor, fromDate, toDate);
            return apiCaller.get(
                path, context.connectionId(), RateLimitGroup.YANDEX_DEFAULT,
                apiKey);
          });

      List<CaptureResult> campaignResults = cursorPagedCapture.captureAllPages(
          context, null, spec, CursorPagedCapture.NO_OP);
      allResults.addAll(campaignResults);

      log.info("Yandex returns: campaignId={} captured, pages={}",
          campaignId, campaignResults.size());
    }

    log.info("Yandex returns capture completed: connectionId={}, campaigns={}, totalPages={}",
        context.connectionId(), campaignIds.size(), allResults.size());
    return allResults;
  }

  private String buildPagePath(
      String basePath, String cursor, LocalDate fromDate, LocalDate toDate) {
    var sb = new StringBuilder(basePath)
        .append("?limit=").append(PAGE_LIMIT)
        .append("&fromDate=").append(fromDate)
        .append("&toDate=").append(toDate);

    if (cursor != null && !cursor.isEmpty()) {
      sb.append("&pageToken=").append(cursor);
    }
    return sb.toString();
  }
}
