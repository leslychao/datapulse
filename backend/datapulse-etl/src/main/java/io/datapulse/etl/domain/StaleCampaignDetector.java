package io.datapulse.etl.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.persistence.canonical.CanonicalPromoCampaignUpsertRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleCampaignDetector {

  private final CanonicalPromoCampaignUpsertRepository campaignRepository;
  private final OutboxService outboxService;
  private final IngestProperties ingestProperties;

  public void detectAndPublish(long connectionId) {
    List<Long> staleCampaignIds = campaignRepository.markStaleCampaigns(
        connectionId, ingestProperties.staleCampaignThreshold());

    if (staleCampaignIds.isEmpty()) {
      log.debug("No stale promo campaigns found: connectionId={}", connectionId);
      return;
    }

    log.info("Marked stale promo campaigns: connectionId={}, count={}, ids={}",
        connectionId, staleCampaignIds.size(), staleCampaignIds);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("connection_id", connectionId);
    payload.put("campaign_ids", staleCampaignIds);

    outboxService.createEvent(
        OutboxEventType.ETL_PROMO_CAMPAIGN_STALE,
        "canonical_promo_campaign",
        connectionId,
        payload);
  }
}
