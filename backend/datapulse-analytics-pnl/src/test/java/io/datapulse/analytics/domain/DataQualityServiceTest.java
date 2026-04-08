package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.DataQualityStatusResponse;
import io.datapulse.analytics.api.DataQualityStatusResponse.ConnectionDataQuality;
import io.datapulse.analytics.api.ReconciliationResultResponse;
import io.datapulse.analytics.config.AnalyticsQueryProperties;
import io.datapulse.analytics.config.AnalyticsQueryProperties.DataQualityProperties;
import io.datapulse.analytics.persistence.DataQualityReadRepository;
import io.datapulse.analytics.persistence.DataQualityReadRepository.BaselineStat;
import io.datapulse.analytics.persistence.DataQualityReadRepository.ReconciliationRow;
import io.datapulse.analytics.persistence.SyncStateReadRepository;
import io.datapulse.analytics.persistence.SyncStateReadRepository.SyncFreshnessRow;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository.ConnectionRow;
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
    var dqProps = new DataQualityProperties(24, 48, 2, 6, 3, 30, 0.05, 30);
    var queryProps = new AnalyticsQueryProperties(null, dqProps);
    service = new DataQualityService(
        dataQualityReadRepository, syncStateReadRepository,
        connectionRepository, queryProps);
  }

  @Nested
  @DisplayName("getStatus")
  class GetStatus {

    @Test
    @DisplayName("should return empty connections when no sync states")
    void should_returnEmpty_when_noSyncStates() {
      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of());

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.connections()).isEmpty();
    }

    @Test
    @DisplayName("should group domains under connection")
    void should_groupDomainsUnderConnection() {
      OffsetDateTime recent = OffsetDateTime.now().minusHours(5);
      var finance = new SyncFreshnessRow(10L, "WB", "WB", "finance", recent);
      var orders = new SyncFreshnessRow(10L, "WB", "WB", "orders", recent);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(finance, orders));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.connections()).hasSize(1);
      ConnectionDataQuality conn = result.connections().get(0);
      assertThat(conn.connectionId()).isEqualTo(10L);
      assertThat(conn.domains()).hasSize(2);
    }

    @Test
    @DisplayName("should mark finance domain as STALE when older than threshold")
    void should_markStale_when_financeOlderThanThreshold() {
      OffsetDateTime staleTime = OffsetDateTime.now().minusHours(30);
      var row = new SyncFreshnessRow(10L, "WB", "WB", "finance", staleTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      var domain = result.connections().get(0).domains().get(0);
      assertThat(domain.status()).isEqualTo("STALE");
    }

    @Test
    @DisplayName("should mark domain as FRESH when within threshold")
    void should_markFresh_when_withinThreshold() {
      OffsetDateTime recentTime = OffsetDateTime.now().minusHours(10);
      var row = new SyncFreshnessRow(10L, "WB", "WB", "finance", recentTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.connections().get(0).domains().get(0).status())
          .isEqualTo("FRESH");
    }

    @Test
    @DisplayName("should mark as OVERDUE when lastSuccessAt is null")
    void should_markOverdue_when_nullLastSuccess() {
      var row = new SyncFreshnessRow(10L, "WB", "WB", "finance", null);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.connections().get(0).domains().get(0).status())
          .isEqualTo("OVERDUE");
    }

    @Test
    @DisplayName("should mark as OVERDUE when far beyond threshold")
    void should_markOverdue_when_farBeyondThreshold() {
      OffsetDateTime veryOld = OffsetDateTime.now().minusHours(200);
      var row = new SyncFreshnessRow(10L, "WB", "WB", "finance", veryOld);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.connections().get(0).domains().get(0).status())
          .isEqualTo("OVERDUE");
    }

    @Test
    @DisplayName("should set automationBlocked when finance domain is not FRESH")
    void should_setBlocked_when_financeDomainStale() {
      OffsetDateTime staleTime = OffsetDateTime.now().minusHours(30);
      var row = new SyncFreshnessRow(10L, "WB", "WB", "finance", staleTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      ConnectionDataQuality conn = result.connections().get(0);
      assertThat(conn.automationBlocked()).isTrue();
      assertThat(conn.blockReason())
          .isEqualTo("analytics.data_quality.block_reason.stale_domain");
      assertThat(conn.blockReasonArgs())
          .containsEntry("domain", "finance")
          .containsEntry("hours", 24);
    }

    @Test
    @DisplayName("should NOT block automation for stale non-finance domain")
    void should_notBlock_when_nonFinanceDomainStale() {
      OffsetDateTime staleTime = OffsetDateTime.now().minusHours(100);
      var row = new SyncFreshnessRow(10L, "WB", "WB", "orders", staleTime);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(row));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.connections().get(0).automationBlocked()).isFalse();
    }

    @Test
    @DisplayName("should use domain-specific threshold hours")
    void should_useDomainThreshold() {
      OffsetDateTime time = OffsetDateTime.now().minusHours(30);
      var finance = new SyncFreshnessRow(10L, "WB", "WB", "finance", time);
      var orders = new SyncFreshnessRow(10L, "WB", "WB", "orders", time);

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(finance, orders));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      var domains = result.connections().get(0).domains();
      // finance: threshold=24h, 30h past → STALE
      assertThat(domains.get(0).status()).isEqualTo("STALE");
      // orders: threshold=48h, 30h past → FRESH
      assertThat(domains.get(1).status()).isEqualTo("FRESH");
    }

    @Test
    @DisplayName("should handle multiple connections")
    void should_handleMultipleConnections() {
      var staleRow = new SyncFreshnessRow(
          10L, "WB Conn", "WB", "finance",
          OffsetDateTime.now().minusHours(30));
      var freshRow = new SyncFreshnessRow(
          20L, "Ozon Conn", "OZON", "finance",
          OffsetDateTime.now().minusHours(5));

      when(syncStateReadRepository.findSyncFreshness(WORKSPACE_ID))
          .thenReturn(List.of(staleRow, freshRow));

      DataQualityStatusResponse result = service.getStatus(WORKSPACE_ID);

      assertThat(result.connections()).hasSize(2);
      assertThat(result.connections().get(0).automationBlocked()).isTrue();
      assertThat(result.connections().get(1).automationBlocked()).isFalse();
    }
  }

  @Nested
  @DisplayName("computeSyncStatus")
  class ComputeSyncStatus {

    @Test
    void should_returnFresh_when_withinThreshold() {
      OffsetDateTime now = OffsetDateTime.now();
      assertThat(service.computeSyncStatus(now.minusHours(10), 24, now))
          .isEqualTo("FRESH");
    }

    @Test
    void should_returnStale_when_beyondThresholdWithinOverdue() {
      OffsetDateTime now = OffsetDateTime.now();
      assertThat(service.computeSyncStatus(now.minusHours(30), 24, now))
          .isEqualTo("STALE");
    }

    @Test
    void should_returnOverdue_when_beyondOverdueThreshold() {
      OffsetDateTime now = OffsetDateTime.now();
      assertThat(service.computeSyncStatus(now.minusHours(100), 24, now))
          .isEqualTo("OVERDUE");
    }

    @Test
    void should_returnOverdue_when_null() {
      assertThat(service.computeSyncStatus(null, 24, OffsetDateTime.now()))
          .isEqualTo("OVERDUE");
    }
  }

  @Nested
  @DisplayName("getReconciliation")
  class GetReconciliation {

    @Test
    @DisplayName("should return empty when no connections")
    void should_returnEmpty_when_noConnections() {
      when(connectionRepository.findActiveByWorkspaceIdAsMap(WORKSPACE_ID))
          .thenReturn(Map.of());

      ReconciliationResultResponse result =
          service.getReconciliation(WORKSPACE_ID, null);

      assertThat(result.connections()).isEmpty();
      assertThat(result.trend()).isEmpty();
      assertThat(result.distribution()).isEmpty();
      verify(dataQualityReadRepository, never()).findReconciliationRows(anyLong());
    }

    @Test
    @DisplayName("should build complete result with connections, trend, distribution")
    void should_buildCompleteResult() {
      var connRow = new ConnectionRow(10L, "WB Shop", "WB");
      when(connectionRepository.findActiveByWorkspaceIdAsMap(WORKSPACE_ID))
          .thenReturn(Map.of(10L, connRow));

      var row = new ReconciliationRow(
          10L, "wb", 202504,
          new BigDecimal("500000"), new BigDecimal("5000"),
          new BigDecimal("0.01"));

      when(dataQualityReadRepository.findReconciliationRows(WORKSPACE_ID))
          .thenReturn(List.of(row));
      when(dataQualityReadRepository.findBaselineStats(WORKSPACE_ID))
          .thenReturn(Map.of("10:wb", new BaselineStat(
              new BigDecimal("0.015"), new BigDecimal("0.005"), 5)));

      ReconciliationResultResponse result =
          service.getReconciliation(WORKSPACE_ID, null);

      assertThat(result.connections()).hasSize(1);
      assertThat(result.connections().get(0).connectionName()).isEqualTo("WB Shop");
      assertThat(result.trend()).hasSize(1);
      assertThat(result.distribution()).isNotEmpty();
    }

    @Test
    @DisplayName("should detect anomaly when residual ratio exceeds threshold")
    void should_detectAnomaly() {
      var connRow = new ConnectionRow(10L, "WB Shop", "WB");
      when(connectionRepository.findActiveByWorkspaceIdAsMap(WORKSPACE_ID))
          .thenReturn(Map.of(10L, connRow));

      var row = new ReconciliationRow(
          10L, "wb", 202504,
          new BigDecimal("500000"), new BigDecimal("50000"),
          new BigDecimal("0.10"));

      when(dataQualityReadRepository.findReconciliationRows(WORKSPACE_ID))
          .thenReturn(List.of(row));
      when(dataQualityReadRepository.findBaselineStats(WORKSPACE_ID))
          .thenReturn(Map.of("10:wb", new BaselineStat(
              new BigDecimal("0.02"), new BigDecimal("0.01"), 200)));

      ReconciliationResultResponse result =
          service.getReconciliation(WORKSPACE_ID, null);

      assertThat(result.connections().get(0).status()).isEqualTo("ANOMALY");
    }

    @Test
    @DisplayName("should return CALIBRATION when few data periods")
    void should_returnCalibration_when_fewPeriods() {
      var connRow = new ConnectionRow(10L, "WB Shop", "WB");
      when(connectionRepository.findActiveByWorkspaceIdAsMap(WORKSPACE_ID))
          .thenReturn(Map.of(10L, connRow));

      var row = new ReconciliationRow(
          10L, "wb", 202504,
          new BigDecimal("500000"), new BigDecimal("1000"),
          new BigDecimal("0.002"));

      when(dataQualityReadRepository.findReconciliationRows(WORKSPACE_ID))
          .thenReturn(List.of(row));
      when(dataQualityReadRepository.findBaselineStats(WORKSPACE_ID))
          .thenReturn(Map.of("10:wb", new BaselineStat(
              new BigDecimal("0.002"), new BigDecimal("0.001"), 3)));

      ReconciliationResultResponse result =
          service.getReconciliation(WORKSPACE_ID, null);

      assertThat(result.connections().get(0).status()).isEqualTo("CALIBRATION");
    }

    @Test
    @DisplayName("should match baseline despite uppercase marketplace_type in PostgreSQL")
    void should_matchBaseline_when_casesDiffer() {
      var connRow = new ConnectionRow(10L, "Ozon Store", "OZON");
      when(connectionRepository.findActiveByWorkspaceIdAsMap(WORKSPACE_ID))
          .thenReturn(Map.of(10L, connRow));

      var row = new ReconciliationRow(
          10L, "ozon", 202504,
          new BigDecimal("1000000"), new BigDecimal("1000"),
          new BigDecimal("0.001"));

      when(dataQualityReadRepository.findReconciliationRows(WORKSPACE_ID))
          .thenReturn(List.of(row));
      when(dataQualityReadRepository.findBaselineStats(WORKSPACE_ID))
          .thenReturn(Map.of("10:ozon", new BaselineStat(
              new BigDecimal("0.005"), new BigDecimal("0.002"), 12)));

      ReconciliationResultResponse result =
          service.getReconciliation(WORKSPACE_ID, null);

      var conn = result.connections().get(0);
      assertThat(conn.status()).isEqualTo("NORMAL");
      assertThat(conn.baselineRatioPct()).isNotEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should match baseline when CH source_platform is uppercase")
    void should_matchBaseline_when_chPlatformUppercase() {
      var connRow = new ConnectionRow(10L, "WB Shop", "WB");
      when(connectionRepository.findActiveByWorkspaceIdAsMap(WORKSPACE_ID))
          .thenReturn(Map.of(10L, connRow));

      var row = new ReconciliationRow(
          10L, "WB", 202504,
          new BigDecimal("500000"), new BigDecimal("2500"),
          new BigDecimal("0.005"));

      when(dataQualityReadRepository.findReconciliationRows(WORKSPACE_ID))
          .thenReturn(List.of(row));
      when(dataQualityReadRepository.findBaselineStats(WORKSPACE_ID))
          .thenReturn(Map.of("10:wb", new BaselineStat(
              new BigDecimal("0.008"), new BigDecimal("0.003"), 10)));

      ReconciliationResultResponse result =
          service.getReconciliation(WORKSPACE_ID, null);

      var conn = result.connections().get(0);
      assertThat(conn.status()).isEqualTo("NORMAL");
      assertThat(conn.baselineRatioPct()).isNotEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.trend().get(0).baselineRatioPct())
          .isNotEqualByComparingTo(BigDecimal.ZERO);
    }
  }
}
