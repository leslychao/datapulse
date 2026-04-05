package io.datapulse.etl.adapter.wb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

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

/**
 * HTTP adapter for WB Advert API.
 * <ul>
 *   <li>{@code GET /api/advert/v2/adverts} — campaign list</li>
 *   <li>{@code GET /adv/v3/fullstats} — campaign statistics (batch by 50 IDs)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WbAdvertisingReadAdapter {

  private static final String CAMPAIGNS_PATH = "/api/advert/v2/adverts";
  private static final String FULLSTATS_PATH = "/adv/v3/fullstats";

  private final WbApiCaller apiCaller;
  private final IntegrationProperties properties;
  private final StreamingPageCapture pageCapture;

  public CaptureResult captureCampaigns(CaptureContext context, String apiToken) {
    String baseUrl = properties.getWildberries().getAdvertBaseUrl();
    Flux<DataBuffer> body = apiCaller.get(
        baseUrl + CAMPAIGNS_PATH,
        apiToken, context.connectionId(), RateLimitGroup.WB_ADVERT);

    PageCaptureResult page = pageCapture.capture(
        body, context, 0, NoCursorExtractor.INSTANCE);

    log.info("WB advertising campaigns captured: connectionId={}, byteSize={}",
        context.connectionId(), page.captureResult().byteSize());
    return page.captureResult();
  }

  public CaptureResult captureFullstats(CaptureContext context, String apiToken,
      List<Long> campaignIds,
      LocalDate beginDate, LocalDate endDate) {
    String baseUrl = properties.getWildberries().getAdvertBaseUrl();
    String idsParam = campaignIds.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(","));

    Flux<DataBuffer> body = apiCaller.get(
        baseUrl + FULLSTATS_PATH,
        uriBuilder -> uriBuilder
            .queryParam("ids", idsParam)
            .queryParam("beginDate",
                beginDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .queryParam("endDate",
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .build(),
        apiToken, context.connectionId(), RateLimitGroup.WB_ADVERT);

    PageCaptureResult page = pageCapture.capture(
        body, context, 0, NoCursorExtractor.INSTANCE);

    log.info("WB advertising fullstats captured: connectionId={}, campaignCount={},"
            + " dateRange={}..{}, byteSize={}",
        context.connectionId(), campaignIds.size(),
        beginDate, endDate, page.captureResult().byteSize());
    return page.captureResult();
  }
}
