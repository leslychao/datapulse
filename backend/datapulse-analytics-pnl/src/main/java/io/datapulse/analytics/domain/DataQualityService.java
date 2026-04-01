package io.datapulse.analytics.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.analytics.api.DataQualityStatusResponse;
import io.datapulse.analytics.api.DataQualityStatusResponse.AutomationBlocker;
import io.datapulse.analytics.api.DataQualityStatusResponse.SyncFreshness;
import io.datapulse.analytics.api.ReconciliationResponse;
import io.datapulse.analytics.config.AnalyticsQueryProperties;
import io.datapulse.analytics.persistence.DataQualityReadRepository;
import io.datapulse.analytics.persistence.SyncStateReadRepository;
import io.datapulse.analytics.persistence.SyncStateReadRepository.SyncFreshnessRow;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataQualityService {

  private final DataQualityReadRepository dataQualityReadRepository;
  private final SyncStateReadRepository syncStateReadRepository;
  private final WorkspaceConnectionRepository connectionRepository;
  private final AnalyticsQueryProperties properties;

  @Transactional(readOnly = true)
  public DataQualityStatusResponse getStatus(long workspaceId) {
    List<SyncFreshnessRow> rows = syncStateReadRepository.findSyncFreshness(workspaceId);

    var freshnessList = new ArrayList<SyncFreshness>();
    var blockerList = new ArrayList<AutomationBlocker>();
    OffsetDateTime now = OffsetDateTime.now();
    var dqProps = properties.dataQuality();

    for (SyncFreshnessRow row : rows) {
      int thresholdHours = resolveThresholdHours(row.dataDomain(), dqProps);
      boolean stale = row.lastSuccessAt() == null
          || row.lastSuccessAt().plusHours(thresholdHours).isBefore(now);

      freshnessList.add(new SyncFreshness(
          row.connectionId(), row.connectionName(), row.sourcePlatform(),
          row.dataDomain(), row.lastSuccessAt(), stale, thresholdHours));

      if (stale && isBlockingDomain(row.dataDomain())) {
        blockerList.add(new AutomationBlocker(
            row.connectionId(), row.connectionName(), row.sourcePlatform(),
            "Stale %s data (>%dh)".formatted(row.dataDomain(), thresholdHours),
            true));
      }
    }

    return new DataQualityStatusResponse(freshnessList, blockerList);
  }

  public List<ReconciliationResponse> getReconciliation(long workspaceId) {
    List<Long> connectionIds = connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
    if (connectionIds.isEmpty()) {
      return List.of();
    }
    return dataQualityReadRepository.findReconciliation(
        connectionIds, properties.dataQuality().residualAnomalyStdMultiplier());
  }

  private int resolveThresholdHours(String domain,
      AnalyticsQueryProperties.DataQualityProperties dqProps) {
    return switch (domain.toLowerCase()) {
      case "finance" -> dqProps.staleFinanceThresholdHours();
      case "advertising" -> dqProps.staleAdvertisingThresholdHours();
      default -> dqProps.staleStateThresholdHours();
    };
  }

  private boolean isBlockingDomain(String domain) {
    return "finance".equalsIgnoreCase(domain);
  }
}
