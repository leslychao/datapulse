package io.datapulse.etl.domain.source.wb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.wb.WbAdvertisingFlattener;
import io.datapulse.etl.adapter.wb.WbAdvertisingReadAdapter;
import io.datapulse.etl.adapter.wb.WbCampaignTypeMapper;
import io.datapulse.etl.adapter.wb.dto.WbAdvertCampaignDto;
import io.datapulse.etl.adapter.wb.dto.WbFullstatsCampaignDto;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.AdvertisingCampaignUpsertRepository;
import io.datapulse.etl.persistence.canonical.CanonicalAdvertisingCampaignEntity;
import io.datapulse.etl.persistence.clickhouse.AdvertisingClickHouseWriter;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WB Advertising ETL pipeline.
 *
 * <ol>
 *   <li>Campaigns list → canonical upsert (PostgreSQL)</li>
 *   <li>Fullstats v3 (batched by 50 IDs) → S3 → flatten → ClickHouse</li>
 *   <li>Stale campaign detection (48h without sync → archived)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WbAdvertisingFactSource implements EventSource {

  private static final int FULLSTATS_BATCH_SIZE = 50;
  private static final Duration STALE_THRESHOLD = Duration.ofHours(48);
  private static final int SMALL_RESPONSE_BYTES = 10;
  private static final BigDecimal KOPECKS_DIVISOR = BigDecimal.valueOf(100);

  private final WbAdvertisingReadAdapter adapter;
  private final WbAdvertisingFlattener flattener;
  private final AdvertisingClickHouseWriter clickHouseWriter;
  private final AdvertisingCampaignUpsertRepository campaignRepository;
  private final SubSourceRunner subSourceRunner;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.WB;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.ADVERTISING_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String token = ctx.credentials().get(CredentialKeys.WB_API_TOKEN);
    List<SubSourceResult> results = new ArrayList<>();

    List<Long> campaignIds = new ArrayList<>();
    SubSourceResult campaignResult = syncCampaigns(ctx, token, campaignIds);
    results.add(campaignResult);

    if (!campaignResult.isSuccess() || campaignIds.isEmpty()) {
      log.info("No WB advertising campaigns to process: connectionId={}, success={}",
          ctx.connectionId(), campaignResult.isSuccess());
      markStaleCampaigns(ctx.connectionId());
      return results;
    }

    SubSourceResult factsResult = syncFacts(ctx, token, campaignIds);
    results.add(factsResult);

    markStaleCampaigns(ctx.connectionId());
    return results;
  }

  private SubSourceResult syncCampaigns(IngestContext ctx, String token,
      List<Long> outCampaignIds) {
    var captureCtx = CaptureContextFactory.build(
        ctx, eventType(), "WbAdCampaigns");
    CaptureResult page = adapter.captureCampaigns(captureCtx, token);

    return subSourceRunner.processPages(
        "WbAdCampaigns", List.of(page), WbAdvertCampaignDto.class,
        batch -> {
          var entities = batch.stream()
              .map(dto -> mapCampaignToEntity(dto, ctx))
              .toList();
          campaignRepository.upsertAll(entities);
          batch.forEach(dto -> outCampaignIds.add(dto.advertId()));
        });
  }

  private SubSourceResult syncFacts(IngestContext ctx, String token,
      List<Long> campaignIds) {
    LocalDate dateFrom = ctx.wbFactDateFrom();
    LocalDate dateTo = skipToday(ctx.wbFactDateTo());

    if (dateFrom.isAfter(dateTo)) {
      log.info("WB ad facts date range empty after skipping today:"
              + " connectionId={}, dateFrom={}, dateTo={}",
          ctx.connectionId(), dateFrom, dateTo);
      return SubSourceResult.success("WbAdFullstats", 0, 0);
    }

    List<CaptureResult> statsPages = captureFullstatsBatched(
        ctx, token, campaignIds, dateFrom, dateTo);

    if (statsPages.isEmpty()) {
      return SubSourceResult.success("WbAdFullstats", 0, 0);
    }

    return subSourceRunner.processPages(
        "WbAdFullstats", statsPages, WbFullstatsCampaignDto.class,
        batch -> {
          var rows = flattener.flatten(
              batch, ctx.connectionId(), ctx.jobExecutionId());
          if (!rows.isEmpty()) {
            clickHouseWriter.writeFacts(rows);
          }
        });
  }

  private List<CaptureResult> captureFullstatsBatched(
      IngestContext ctx, String token,
      List<Long> campaignIds,
      LocalDate dateFrom, LocalDate dateTo) {

    List<CaptureResult> statsPages = new ArrayList<>();
    List<List<Long>> batches = partition(campaignIds, FULLSTATS_BATCH_SIZE);

    for (int i = 0; i < batches.size(); i++) {
      var captureCtx = CaptureContextFactory.build(
          ctx, eventType(), "WbAdFullstats-" + i);
      try {
        CaptureResult page = adapter.captureFullstats(
            captureCtx, token, batches.get(i), dateFrom, dateTo);
        if (page.byteSize() >= SMALL_RESPONSE_BYTES) {
          statsPages.add(page);
        } else {
          log.debug("Skipping empty fullstats response:"
                  + " connectionId={}, batch={}",
              ctx.connectionId(), i);
        }
      } catch (Exception e) {
        log.warn("Failed to capture fullstats batch:"
                + " connectionId={}, batch={}, error={}",
            ctx.connectionId(), i, e.getMessage(), e);
      }
    }

    return statsPages;
  }

  private CanonicalAdvertisingCampaignEntity mapCampaignToEntity(
      WbAdvertCampaignDto dto, IngestContext ctx) {
    var entity = new CanonicalAdvertisingCampaignEntity();
    entity.setConnectionId(ctx.connectionId());
    entity.setExternalCampaignId(String.valueOf(dto.advertId()));
    entity.setName(dto.name());
    entity.setCampaignType(WbCampaignTypeMapper.mapType(dto.type()));
    entity.setStatus(WbCampaignTypeMapper.mapStatus(dto.status()));
    entity.setDailyBudget(
        BigDecimal.valueOf(dto.dailyBudget())
            .divide(KOPECKS_DIVISOR, 2, RoundingMode.HALF_UP));
    entity.setStartTime(parseDateTime(dto.startTime()));
    entity.setEndTime(parseDateTime(dto.endTime()));
    entity.setCreatedAtExternal(parseDateTime(dto.createTime()));
    entity.setSyncedAt(OffsetDateTime.now());
    return entity;
  }

  private OffsetDateTime parseDateTime(String dt) {
    if (dt == null || dt.isBlank()) {
      return null;
    }
    return OffsetDateTime.parse(dt);
  }

  private LocalDate skipToday(LocalDate dateTo) {
    LocalDate today = LocalDate.now();
    if (!dateTo.isBefore(today)) {
      return today.minusDays(1);
    }
    return dateTo;
  }

  private void markStaleCampaigns(long connectionId) {
    try {
      List<Long> staleIds = campaignRepository.markStaleCampaigns(
          connectionId, STALE_THRESHOLD);
      if (!staleIds.isEmpty()) {
        log.info("Marked {} stale WB advertising campaigns:"
            + " connectionId={}", staleIds.size(), connectionId);
      }
    } catch (Exception e) {
      log.warn("Failed to mark stale campaigns: connectionId={}",
          connectionId, e);
    }
  }

  private static <T> List<List<T>> partition(List<T> list, int size) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      partitions.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return partitions;
  }
}
