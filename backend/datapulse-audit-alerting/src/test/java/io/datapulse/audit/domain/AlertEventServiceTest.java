package io.datapulse.audit.domain;

import io.datapulse.audit.api.AlertEventFilter;
import io.datapulse.audit.api.AlertEventResponse;
import io.datapulse.audit.domain.event.AlertEventCreatedEvent;
import io.datapulse.audit.domain.event.AlertResolvedEvent;
import io.datapulse.audit.persistence.AlertEventRepository;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEventServiceTest {

  @Mock
  private AlertEventRepository alertEventRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private AlertEventService alertEventService;

  private AlertEventResponse buildAlert(long id, String status) {
    return new AlertEventResponse(
        id, null, 1L, "WB", status, "WARNING",
        "Test alert", null, false,
        OffsetDateTime.now(), null, null, null, null);
  }

  @Nested
  @DisplayName("acknowledge")
  class Acknowledge {

    @Test
    @DisplayName("should_acknowledge_when_alert_in_open_state")
    void should_acknowledge_when_alert_in_open_state() {
      var acknowledged = buildAlert(1L, "ACKNOWLEDGED");
      when(alertEventRepository.acknowledge(1L, 1L, 10L)).thenReturn(1);
      when(alertEventRepository.findById(1L, 1L))
          .thenReturn(Optional.of(acknowledged));

      AlertEventResponse result = alertEventService.acknowledge(1L, 1L, 10L);

      assertThat(result.status()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    @DisplayName("should_throw_not_found_when_alert_missing")
    void should_throw_not_found_when_alert_missing() {
      when(alertEventRepository.acknowledge(99L, 1L, 10L)).thenReturn(0);
      when(alertEventRepository.findById(99L, 1L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> alertEventService.acknowledge(99L, 1L, 10L))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("should_throw_bad_request_when_alert_not_in_open_state")
    void should_throw_bad_request_when_alert_not_in_open_state() {
      var existing = buildAlert(1L, "RESOLVED");
      when(alertEventRepository.acknowledge(1L, 1L, 10L)).thenReturn(0);
      when(alertEventRepository.findById(1L, 1L))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> alertEventService.acknowledge(1L, 1L, 10L))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("resolve")
  class Resolve {

    @Test
    @DisplayName("should_resolve_and_publish_event_when_acknowledged")
    void should_resolve_and_publish_event_when_acknowledged() {
      var before = buildAlert(1L, "ACKNOWLEDGED");
      var after = buildAlert(1L, "RESOLVED");

      when(alertEventRepository.findById(1L, 1L))
          .thenReturn(Optional.of(before))
          .thenReturn(Optional.of(after));
      when(alertEventRepository.resolve(1L, 1L)).thenReturn(1);

      AlertEventResponse result = alertEventService.resolve(1L, 1L);

      assertThat(result.status()).isEqualTo("RESOLVED");

      ArgumentCaptor<AlertResolvedEvent> captor =
          ArgumentCaptor.forClass(AlertResolvedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().resolvedReason()).isEqualTo("MANUAL");
      assertThat(captor.getValue().alertEventId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_throw_not_found_when_alert_missing")
    void should_throw_not_found_when_alert_missing() {
      when(alertEventRepository.findById(99L, 1L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> alertEventService.resolve(99L, 1L))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("should_throw_bad_request_when_not_acknowledged")
    void should_throw_bad_request_when_not_acknowledged() {
      var before = buildAlert(1L, "OPEN");
      when(alertEventRepository.findById(1L, 1L))
          .thenReturn(Optional.of(before));
      when(alertEventRepository.resolve(1L, 1L)).thenReturn(0);

      assertThatThrownBy(() -> alertEventService.resolve(1L, 1L))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("createRuleBasedAlert")
  class CreateRuleBasedAlert {

    @Test
    @DisplayName("should_insert_and_publish_event_when_called")
    void should_insert_and_publish_event_when_called() {
      when(alertEventRepository.insertRuleBased(
          eq(100L), eq(1L), eq(10L), eq("WARNING"),
          eq("Stale data"), anyString(), eq(true)))
          .thenReturn(42L);

      long id = alertEventService.createRuleBasedAlert(
          100L, 1L, 10L, "STALE_DATA",
          "WARNING", "Stale data", "{}", true);

      assertThat(id).isEqualTo(42L);

      ArgumentCaptor<AlertEventCreatedEvent> captor =
          ArgumentCaptor.forClass(AlertEventCreatedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().ruleType()).isEqualTo("STALE_DATA");
      assertThat(captor.getValue().blocksAutomation()).isTrue();
    }
  }

  @Nested
  @DisplayName("autoResolve")
  class AutoResolve {

    @Test
    @DisplayName("should_resolve_active_alerts_and_publish_events")
    void should_resolve_active_alerts_and_publish_events() {
      var active1 = buildAlert(1L, "OPEN");
      var active2 = buildAlert(2L, "ACKNOWLEDGED");

      when(alertEventRepository.findActiveByRuleAndConnection(100L, 10L))
          .thenReturn(List.of(active1, active2));
      when(alertEventRepository.autoResolve(100L, 10L)).thenReturn(2);

      int resolved = alertEventService.autoResolve(100L, 10L, 1L);

      assertThat(resolved).isEqualTo(2);
      verify(eventPublisher, times(2)).publishEvent(any(AlertResolvedEvent.class));

      ArgumentCaptor<AlertResolvedEvent> captor =
          ArgumentCaptor.forClass(AlertResolvedEvent.class);
      verify(eventPublisher, times(2)).publishEvent(captor.capture());
      captor.getAllValues().forEach(e ->
          assertThat(e.resolvedReason()).isEqualTo("AUTO"));
    }

    @Test
    @DisplayName("should_return_zero_when_no_active_alerts")
    void should_return_zero_when_no_active_alerts() {
      when(alertEventRepository.findActiveByRuleAndConnection(100L, 10L))
          .thenReturn(List.of());

      int resolved = alertEventService.autoResolve(100L, 10L, 1L);

      assertThat(resolved).isZero();
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("existsBlockingAlert")
  class ExistsBlockingAlert {

    @Test
    @DisplayName("should_return_true_when_blocking_alert_exists")
    void should_return_true_when_blocking_alert_exists() {
      when(alertEventRepository.existsBlockingAlert(1L, 10L)).thenReturn(true);

      assertThat(alertEventService.existsBlockingAlert(1L, 10L)).isTrue();
    }

    @Test
    @DisplayName("should_return_false_when_no_blocking_alert")
    void should_return_false_when_no_blocking_alert() {
      when(alertEventRepository.existsBlockingAlert(1L, 10L)).thenReturn(false);

      assertThat(alertEventService.existsBlockingAlert(1L, 10L)).isFalse();
    }
  }

  @Nested
  @DisplayName("listAlerts")
  class ListAlerts {

    @Test
    @DisplayName("should_return_paginated_alerts_with_default_sort")
    void should_return_paginated_alerts_with_default_sort() {
      var filter = new AlertEventFilter(null, null, null);
      var pageable = PageRequest.of(0, 10);
      var alert = buildAlert(1L, "OPEN");

      when(alertEventRepository.findAll(eq(1L), eq(filter), eq("openedAt"), eq(10), eq(0L)))
          .thenReturn(List.of(alert));
      when(alertEventRepository.count(1L, filter)).thenReturn(1L);

      Page<AlertEventResponse> result = alertEventService.listAlerts(1L, filter, pageable);

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getTotalElements()).isEqualTo(1L);
    }
  }
}
