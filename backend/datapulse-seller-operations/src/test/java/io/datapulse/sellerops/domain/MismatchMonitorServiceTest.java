package io.datapulse.sellerops.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import io.datapulse.sellerops.config.MismatchProperties;
import io.datapulse.sellerops.persistence.PriceMismatchJdbcRepository;
import io.datapulse.sellerops.persistence.PriceMismatchJdbcRepository.PriceMismatchCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MismatchMonitorServiceTest {

  private static final long WORKSPACE_ID = 1L;

  @Mock
  private PriceMismatchJdbcRepository mismatchRepository;
  @Mock
  private MismatchProperties mismatchProperties;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private ObjectMapper objectMapper;

  @InjectMocks
  private MismatchMonitorService service;

  @Nested
  @DisplayName("checkPriceMismatches")
  class CheckPriceMismatches {

    @Test
    void should_publish_alert_when_mismatch_found() throws Exception {
      when(mismatchProperties.getPriceWarningThresholdPct())
          .thenReturn(new BigDecimal("1"));
      when(mismatchProperties.getPriceCriticalThresholdPct())
          .thenReturn(new BigDecimal("5"));

      var mismatch = new PriceMismatchCandidate(
          100L, "Test Product", "SKU-001", "WB Connection",
          5L, WORKSPACE_ID,
          new BigDecimal("1000"), new BigDecimal("900"));

      when(mismatchRepository.findPriceMismatches(WORKSPACE_ID, new BigDecimal("1")))
          .thenReturn(List.of(mismatch));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.checkPriceMismatches(WORKSPACE_ID);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());

      AlertTriggeredEvent event = captor.getValue();
      assertThat(event.workspaceId()).isEqualTo(WORKSPACE_ID);
      assertThat(event.connectionId()).isEqualTo(5L);
      assertThat(event.title()).contains("SKU-001");
    }

    @Test
    void should_classify_as_critical_when_delta_exceeds_threshold() throws Exception {
      when(mismatchProperties.getPriceWarningThresholdPct())
          .thenReturn(new BigDecimal("1"));
      when(mismatchProperties.getPriceCriticalThresholdPct())
          .thenReturn(new BigDecimal("5"));

      var mismatch = new PriceMismatchCandidate(
          100L, "Product", "SKU-001", "Connection",
          5L, WORKSPACE_ID,
          new BigDecimal("1000"), new BigDecimal("500"));

      when(mismatchRepository.findPriceMismatches(WORKSPACE_ID, new BigDecimal("1")))
          .thenReturn(List.of(mismatch));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.checkPriceMismatches(WORKSPACE_ID);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().severity()).isEqualTo(MismatchSeverity.CRITICAL.name());
    }

    @Test
    void should_classify_as_warning_when_delta_below_critical() throws Exception {
      when(mismatchProperties.getPriceWarningThresholdPct())
          .thenReturn(new BigDecimal("1"));
      when(mismatchProperties.getPriceCriticalThresholdPct())
          .thenReturn(new BigDecimal("50"));

      var mismatch = new PriceMismatchCandidate(
          100L, "Product", "SKU-001", "Connection",
          5L, WORKSPACE_ID,
          new BigDecimal("1020"), new BigDecimal("1000"));

      when(mismatchRepository.findPriceMismatches(WORKSPACE_ID, new BigDecimal("1")))
          .thenReturn(List.of(mismatch));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.checkPriceMismatches(WORKSPACE_ID);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().severity()).isEqualTo(MismatchSeverity.WARNING.name());
    }

    @Test
    void should_not_publish_when_no_mismatches() {
      when(mismatchProperties.getPriceWarningThresholdPct())
          .thenReturn(new BigDecimal("1"));
      when(mismatchRepository.findPriceMismatches(WORKSPACE_ID, new BigDecimal("1")))
          .thenReturn(List.of());

      service.checkPriceMismatches(WORKSPACE_ID);

      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_treat_zero_expected_as_100pct_delta() throws Exception {
      when(mismatchProperties.getPriceWarningThresholdPct())
          .thenReturn(new BigDecimal("1"));
      when(mismatchProperties.getPriceCriticalThresholdPct())
          .thenReturn(new BigDecimal("5"));

      var mismatch = new PriceMismatchCandidate(
          100L, "Product", "SKU-001", "Connection",
          5L, WORKSPACE_ID,
          new BigDecimal("500"), BigDecimal.ZERO);

      when(mismatchRepository.findPriceMismatches(WORKSPACE_ID, new BigDecimal("1")))
          .thenReturn(List.of(mismatch));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.checkPriceMismatches(WORKSPACE_ID);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().severity()).isEqualTo(MismatchSeverity.CRITICAL.name());
    }

    @Test
    void should_publish_multiple_alerts_for_multiple_mismatches() throws Exception {
      when(mismatchProperties.getPriceWarningThresholdPct())
          .thenReturn(new BigDecimal("1"));
      when(mismatchProperties.getPriceCriticalThresholdPct())
          .thenReturn(new BigDecimal("5"));

      var m1 = new PriceMismatchCandidate(
          1L, "P1", "SKU-1", "C1", 5L, WORKSPACE_ID,
          new BigDecimal("1100"), new BigDecimal("1000"));
      var m2 = new PriceMismatchCandidate(
          2L, "P2", "SKU-2", "C2", 6L, WORKSPACE_ID,
          new BigDecimal("2000"), new BigDecimal("1000"));

      when(mismatchRepository.findPriceMismatches(WORKSPACE_ID, new BigDecimal("1")))
          .thenReturn(List.of(m1, m2));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.checkPriceMismatches(WORKSPACE_ID);

      ArgumentCaptor<AlertTriggeredEvent> captor =
          ArgumentCaptor.forClass(AlertTriggeredEvent.class);
      verify(eventPublisher, org.mockito.Mockito.times(2))
          .publishEvent(captor.capture());
      assertThat(captor.getAllValues()).hasSize(2);
    }
  }
}
