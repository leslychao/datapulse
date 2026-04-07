package io.datapulse.etl.domain.source.ozon;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.datapulse.etl.adapter.ozon.OzonAdvertisingReadAdapter;
import io.datapulse.etl.adapter.ozon.OzonPerformanceTokenService;
import io.datapulse.etl.adapter.ozon.dto.OzonPerformanceCampaignDto;
import io.datapulse.etl.adapter.ozon.dto.OzonPerformanceStatDto;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.CredentialResolver;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.clickhouse.AdvertisingClickHouseWriter;
import io.datapulse.etl.persistence.clickhouse.AdvertisingFactRow;
import io.datapulse.etl.persistence.canonical.AdvertisingCampaignUpsertRepository;
import io.datapulse.etl.persistence.canonical.CanonicalAdvertisingCampaignEntity;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ozon Advertising ETL event source.
 * <p>
 * Algorithm:
 * <ol>
 *   <li>Resolve performance credentials ({@code perfSecretReferenceId}); skip if absent</li>
 *   <li>Obtain OAuth2 token via {@link OzonPerformanceTokenService}</li>
 *   <li>Fetch campaign list → canonical upsert (PostgreSQL)</li>
 *   <li>Fetch SKU-level statistics → S3 capture → ClickHouse write</li>
 *   <li>Mark stale campaigns (not returned in sync > 48h)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OzonAdvertisingFactSource implements EventSource {

  private static final String SUB_SOURCE_CAMPAIGNS = "OzonAdCampaigns";
  private static final String SUB_SOURCE_STATS = "OzonAdStats";
  private static final Duration STALE_THRESHOLD = Duration.ofHours(48);

  private final CredentialResolver credentialResolver;
  private final OzonPerformanceTokenService tokenService;
  private final OzonAdvertisingReadAdapter adapter;
  private final SubSourceRunner subSourceRunner;
  private final AdvertisingCampaignUpsertRepository campaignRepository;
  private final AdvertisingClickHouseWriter clickHouseWriter;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.OZON;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.ADVERTISING_FACT;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    Optional<Map<String, String>> perfCreds =
        credentialResolver.resolvePerformanceCredentials(ctx.connectionId());

    if (perfCreds.isEmpty()) {
      log.info("No performance credentials configured, skipping ADVERTISING_FACT: "
          + "connectionId={}", ctx.connectionId());
      return List.of(SubSourceResult.success(SUB_SOURCE_CAMPAIGNS, 0, 0));
    }

    String perfClientId = perfCreds.get().get(CredentialKeys.OZON_PERFORMANCE_CLIENT_ID);
    String perfClientSecret = perfCreds.get().get(CredentialKeys.OZON_PERFORMANCE_CLIENT_SECRET);

    if (perfClientId == null || perfClientSecret == null) {
      log.warn("Performance credentials incomplete (missing clientId or clientSecret), "
          + "skipping ADVERTISING_FACT: connectionId={}", ctx.connectionId());
      return List.of(SubSourceResult.success(SUB_SOURCE_CAMPAIGNS, 0, 0));
    }

    String accessToken;
    try {
      accessToken = tokenService.getAccessToken(perfClientId, perfClientSecret);
    } catch (Exception e) {
      log.error("Failed to obtain Ozon Performance OAuth2 token: connectionId={}, error={}",
          ctx.connectionId(), e.getMessage(), e);
      return List.of(SubSourceResult.failed(SUB_SOURCE_CAMPAIGNS,
          "OAuth2 token acquisition failed: " + e.getMessage()));
    }

    List<SubSourceResult> results = new ArrayList<>();

    SubSourceResult campaignResult = syncCampaigns(ctx, accessToken);
    results.add(campaignResult);

    if (!campaignResult.isSuccess()) {
      log.warn("Ozon advertising campaigns sync failed, skipping statistics: "
          + "connectionId={}", ctx.connectionId());
      return results;
    }

    SubSourceResult statsResult = syncStatistics(ctx, accessToken);
    results.add(statsResult);

    markStaleCampaigns(ctx.connectionId());

    return results;
  }

  private SubSourceResult syncCampaigns(IngestContext ctx, String accessToken) {
    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SUB_SOURCE_CAMPAIGNS);

    List<CaptureResult> pages;
    try {
      pages = adapter.captureCampaigns(captureCtx, accessToken);
    } catch (Exception e) {
      log.error("Ozon advertising campaigns capture failed: connectionId={}, error={}",
          ctx.connectionId(), e.getMessage(), e);
      return SubSourceResult.failed(SUB_SOURCE_CAMPAIGNS,
          "Campaigns capture failed: " + e.getMessage());
    }

    return subSourceRunner.processPages(
        SUB_SOURCE_CAMPAIGNS, pages, OzonPerformanceCampaignDto.class,
        batch -> processCampaignBatch(batch, ctx));
  }

  private void processCampaignBatch(List<OzonPerformanceCampaignDto> batch, IngestContext ctx) {
    List<CanonicalAdvertisingCampaignEntity> entities = batch.stream()
        .map(dto -> mapToCanonicalCampaign(dto, ctx.connectionId()))
        .toList();

    campaignRepository.upsertAll(entities);
  }

  private CanonicalAdvertisingCampaignEntity mapToCanonicalCampaign(
      OzonPerformanceCampaignDto dto, long connectionId) {
    var entity = new CanonicalAdvertisingCampaignEntity();
    entity.setConnectionId(connectionId);
    entity.setExternalCampaignId(String.valueOf(dto.id()));
    entity.setName(dto.title());
    entity.setCampaignType(dto.advObjectType() != null ? dto.advObjectType() : "UNKNOWN");
    entity.setStatus(mapOzonCampaignStatus(dto.state()));
    entity.setDailyBudget(dto.dailyBudget());
    entity.setStartTime(parseOzonDateTime(dto.createdAt()));
    entity.setEndTime(parseOzonDateTime(dto.endedAt()));
    entity.setCreatedAtExternal(parseOzonDateTime(dto.createdAt()));
    entity.setSyncedAt(OffsetDateTime.now());
    return entity;
  }

  private SubSourceResult syncStatistics(IngestContext ctx, String accessToken) {
    var captureCtx = CaptureContextFactory.build(ctx, eventType(), SUB_SOURCE_STATS);

    LocalDate dateFrom = ctx.ozonFactSince() != null
        ? ctx.ozonFactSince().toLocalDate()
        : LocalDate.now().minusDays(30);
    LocalDate dateTo = ctx.ozonFactTo() != null
        ? ctx.ozonFactTo().toLocalDate()
        : LocalDate.now();

    List<Long> campaignIds = findActiveCampaignIds(ctx.connectionId());

    List<CaptureResult> pages;
    try {
      pages = adapter.captureStatistics(
          captureCtx, accessToken, campaignIds, dateFrom, dateTo);
    } catch (Exception e) {
      log.error("Ozon advertising statistics capture failed: connectionId={}, error={}",
          ctx.connectionId(), e.getMessage(), e);
      return SubSourceResult.failed(SUB_SOURCE_STATS,
          "Statistics capture failed: " + e.getMessage());
    }

    if (pages.isEmpty()) {
      return SubSourceResult.success(SUB_SOURCE_STATS, 0, 0);
    }

    return subSourceRunner.processPages(
        SUB_SOURCE_STATS, pages, OzonPerformanceStatDto.class,
        batch -> processStatsBatch(batch, ctx));
  }

  private void processStatsBatch(List<OzonPerformanceStatDto> batch, IngestContext ctx) {
    List<AdvertisingFactRow> rows = batch.stream()
        .map(dto -> AdvertisingFactRow.fromOzon(
            ctx.connectionId(),
            dto.campaignId(),
            LocalDate.parse(dto.date()),
            String.valueOf(dto.sku()),
            dto.views(),
            dto.clicks(),
            dto.spend() != null ? dto.spend() : BigDecimal.ZERO,
            dto.orders(),
            dto.revenue() != null ? dto.revenue() : BigDecimal.ZERO,
            ctx.jobExecutionId()))
        .toList();

    clickHouseWriter.writeFacts(rows);
  }

  private List<Long> findActiveCampaignIds(long connectionId) {
    return campaignRepository.findActiveCampaignIds(connectionId);
  }

  private void markStaleCampaigns(long connectionId) {
    try {
      List<Long> staleIds = campaignRepository.markStaleCampaigns(
          connectionId, STALE_THRESHOLD);
      if (!staleIds.isEmpty()) {
        log.info("Marked stale advertising campaigns: connectionId={}, count={}",
            connectionId, staleIds.size());
      }
    } catch (Exception e) {
      log.warn("Failed to mark stale advertising campaigns: connectionId={}, error={}",
          connectionId, e.getMessage(), e);
    }
  }

  private static String mapOzonCampaignStatus(String ozonState) {
    if (ozonState == null) {
      return "unknown";
    }
    return switch (ozonState) {
      case "CAMPAIGN_STATE_RUNNING" -> "active";
      case "CAMPAIGN_STATE_PLANNED" -> "ready";
      case "CAMPAIGN_STATE_STOPPED" -> "on_pause";
      case "CAMPAIGN_STATE_INACTIVE" -> "archived";
      case "CAMPAIGN_STATE_ARCHIVED" -> "archived";
      case "CAMPAIGN_STATE_MODERATION" -> "moderation";
      default -> ozonState.toLowerCase()
          .replace("campaign_state_", "");
    };
  }

  private static OffsetDateTime parseOzonDateTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (Exception e) {
      return null;
    }
  }
}
