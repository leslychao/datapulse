package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import io.datapulse.analytics.api.DataQualityStatusResponse;
import io.datapulse.analytics.api.DataQualityStatusResponse.AutomationBlocker;
import io.datapulse.analytics.api.DataQualityStatusResponse.SyncFreshness;
import io.datapulse.analytics.api.ReconciliationResponse;
import io.datapulse.analytics.config.AnalyticsQueryProperties;
import io.datapulse.analytics.config.AnalyticsQueryProperties.DataQualityProperties;
import io.datapulse.analytics.persistence.DataQualityReadRepository;
import io.datapulse.analytics.persistence.SyncStateReadRepository;
import io.datapulse.analytics.persistence.SyncStateReadRepository.SyncFreshnessRow;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataQualityService")
class DataQualityServiceTest {

  @Mock private DataQualityReadRepository dataQualityReadRepository;
  @Mock private SyncStateReadRepository syncStateReadRepository;
  @Mock private WorkspaceConnectionRepository connectionRepository;

  private DataQualityService service;

  private static final long WORKSPACE_ID = 1L;

  @BeforeEach
  void setUp() {
    var dqProps = new DataQualityProperties(24, 48, 2, 100, 3, 30, 0.05, 30);
    var queryProps = new AnalyticsQueryProperties(null, dqProps);
    service = new DataQualityService(
        dataQualityReadRepository, syncStateReadRepository,
        connectionRepository, queryProps);
  }

  @Nested
  @DisplayName("getStatus")
  class GetStatus {

    @Test
    @DisplayName("should return empty freshness when no sync states")
    void should_returnEmptyFreshness_when_noSyncStates() {
      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of());

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.syncFreshness()).isEmpty();
      assertThat(result.automationBlockers()).isEmpty();
    }

    @Test
    @DisplayName("should mark finance domain as stale when older than threshold")
    void should_markStale_when_financeDataOlderThanThreshold() {
      OffsetDateTime staleTime = OffsetDateTime.now().minusHours(25);
      var row = new SyncFreshnessRow(
          10L, "WB Connection", "WB", "finance", staleTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.syncFreshness()).hasSize(1);
      SyncFreshness freshness = result.syncFreshness().get(0);
      assertThat(freshness.stale()).isTrue();
      assertThat(freshness.thresholdHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("should mark finance domain as fresh when within threshold")
    void should_markFresh_when_financeDataWithinThreshold() {
      OffsetDateTime recentTime = OffsetDateTime.now().minusHours(10);
      var row = new SyncFreshnessRow(
          10L, "WB Connection", "WB", "finance", recentTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      SyncFreshness freshness = result.syncFreshness().get(0);
      assertThat(freshness.stale()).isFalse();
    }

    @Test
    @DisplayName("should mark as stale when lastSuccessAt is null")
    void should_markStale_when_lastSuccessAtIsNull() {
      var row = new SyncFreshnessRow(
          10L, "WB Connection", "WB", "finance", null);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.syncFreshness().get(0).stale()).isTrue();
    }

    @Test
    @DisplayName("should create automation blocker for stale finance domain")
    void should_createBlocker_when_financeDomainIsStale() {
      OffsetDateTime staleTime = OffsetDateTime.now().minusHours(30);
      var row = new SyncFreshnessRow(
          10L, "WB Connection", "WB", "finance", staleTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.automationBlockers()).hasSize(1);
      AutomationBlocker blocker = result.automationBlockers().get(0);
      assertThat(blocker.connectionId()).isEqualTo(10L);
      assertThat(blocker.blocked()).isTrue();
      assertThat(blocker.reason()).contains("finance");
    }

    @Test
    @DisplayName("should NOT create blocker for stale non-finance domain")
    void should_notCreateBlocker_when_nonFinanceDomainIsStale() {
      OffsetDateTime staleTime = OffsetDateTime.now().minusHours(100);
      var row = new SyncFreshnessRow(
          10L, "WB Connection", "WB", "orders", staleTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.syncFreshness().get(0).stale()).isTrue();
      assertThat(result.automationBlockers()).isEmpty();
    }

    @Test
    @DisplayName("should use domain-specific threshold hours")
    void should_useDomainThreshold_when_differentDomainsChecked() {
      OffsetDateTime time = OffsetDateTime.now().minusHours(50);
      var financeRow = new SyncFreshnessRow(
          10L, "WB Connection", "WB", "finance", time);
      var otherRow = new SyncFreshnessRow(
          10L, "WB Connection", "WB", "orders", time);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(financeRow, otherRow));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.syncFreshness()).hasSize(2);
      // finance: threshold=24, 50h > 24h → stale
      assertThat(result.syncFreshness().get(0).stale()).isTrue();
      assertThat(result.syncFreshness().get(0).thresholdHours()).isEqualTo(24);
      // orders (default): threshold=48, 50h > 48h → stale
      assertThat(result.syncFreshness().get(1).stale()).isTrue();
      assertThat(result.syncFreshness().get(1).thresholdHours()).isEqualTo(48);
    }

    @Test
    @DisplayName("should handle multiple connections with mixed staleness")
    void should_handleMixedStaleness_when_multipleConnections() {
      var staleRow = new SyncFreshnessRow(
          10L, "WB Conn", "WB", "finance",
          OffsetDateTime.now().minusHours(30));
      var freshRow = new SyncFreshnessRow(
          20L, "Ozon Conn", "OZON", "finance",
          OffsetDateTime.now().minusHours(5));

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(staleRow, freshRow));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.syncFreshness()).hasSize(2);
      assertThat(result.syncFreshness().get(0).stale()).isTrue();
      assertThat(result.syncFreshness().get(1).stale()).isFalse();
      assertThat(result.automationBlockers()).hasSize(1);
      assertThat(result.automationBlockers().get(0).connectionId()).isEqualTo(10L);
    }
  }

  @Nested
  @DisplayName("getReconciliation")
  class GetReconciliation {

    @Test
    @DisplayName("should return empty list when no connections")
    void should_returnEmpty_when_noConnections() {
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(List.of());

      List<ReconciliationResponse> result = service.getReconciliation(WORKSPACE_ID);

      assertThat(result).isEmpty();
      verify(dataQualityReadRepository, never()).findReconciliation(anyList(), anyInt());
    }

    @Test
    @DisplayName("should detect anomaly when residual ratio exceeds threshold")
    void should_detectAnomaly_when_residualRatioHigh() {
      List<Long> connIds = List.of(10L);
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(connIds);

      var recon = new ReconciliationResponse(
          10L, "WB", 202501,
          new BigDecimal("500000.00"), new BigDecimal("450000.00"),
          new BigDecimal("50000.00"), new BigDecimal("0.10"),
          new BigDecimal("0.02"), true);

      when(dataQualityReadRepository.findReconciliation(connIds, 2))
          .thenReturn(List.of(recon));

      List<ReconciliationResponse> result = service.getReconciliation(WORKSPACE_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).anomaly()).isTrue();
      assertThat(result.get(0).residualRatio()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("should not flag anomaly when residual within normal range")
    void should_notFlagAnomaly_when_residualNormal() {
      List<Long> connIds = List.of(10L);
      when(connectionRepository.findConnectionIdsByWorkspaceId(WORKSPACE_ID))
          .thenReturn(connIds);

      var recon = new ReconciliationResponse(
          10L, "WB", 202501,
          new BigDecimal("500000.00"), new BigDecimal("499000.00"),
          new BigDecimal("1000.00"), new BigDecimal("0.002"),
          new BigDecimal("0.003"), false);

      when(dataQualityReadRepository.findReconciliation(connIds, 2))
          .thenReturn(List.of(recon));

      List<ReconciliationResponse> result = service.getReconciliation(WORKSPACE_ID);

      assertThat(result.get(0).anomaly()).isFalse();
    }
  }
}
