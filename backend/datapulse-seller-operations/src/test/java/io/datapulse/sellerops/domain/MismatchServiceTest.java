package io.datapulse.sellerops.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.MismatchFilter;
import io.datapulse.sellerops.api.MismatchResponse;
import io.datapulse.sellerops.config.MismatchProperties;
import io.datapulse.sellerops.persistence.MismatchJdbcRepository;
import io.datapulse.sellerops.persistence.MismatchRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MismatchServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long MISMATCH_ID = 100L;
  private static final long USER_ID = 10L;

  private static final MismatchFilter EMPTY_FILTER =
      new MismatchFilter(null, null, null, null, null, null, null, null);

  @Mock
  private MismatchJdbcRepository mismatchRepository;
  @Mock
  private MismatchProperties mismatchProperties;
  @Mock
  private MismatchStompPublisher stompPublisher;

  @InjectMocks
  private MismatchService service;

  @Nested
  @DisplayName("listMismatches")
  class ListMismatches {

    @Test
    void should_return_mapped_page() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = buildMismatchRow("OPEN");

      when(mismatchRepository.findAll(WORKSPACE_ID, EMPTY_FILTER, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<MismatchResponse> result = service.listMismatches(WORKSPACE_ID, EMPTY_FILTER, pageable);

      assertThat(result.getContent()).hasSize(1);
      MismatchResponse response = result.getContent().get(0);
      assertThat(response.skuCode()).isEqualTo("SKU-001");
      assertThat(response.mismatchId()).isEqualTo(MISMATCH_ID);
      assertThat(response.type()).isEqualTo("PRICE");
      assertThat(response.severity()).isEqualTo("WARNING");
      assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void should_map_ignored_status_from_resolved_reason() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = buildMismatchRow("RESOLVED");
      row.setResolvedReason("IGNORED");

      when(mismatchRepository.findAll(WORKSPACE_ID, EMPTY_FILTER, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<MismatchResponse> result = service.listMismatches(WORKSPACE_ID, EMPTY_FILTER, pageable);

      assertThat(result.getContent().get(0).status()).isEqualTo("IGNORED");
    }

    @Test
    void should_return_empty_page_when_no_results() {
      Pageable pageable = PageRequest.of(0, 20);

      when(mismatchRepository.findAll(WORKSPACE_ID, EMPTY_FILTER, pageable))
          .thenReturn(Page.empty(pageable));

      Page<MismatchResponse> result = service.listMismatches(WORKSPACE_ID, EMPTY_FILTER, pageable);

      assertThat(result.getContent()).isEmpty();
      assertThat(result.getTotalElements()).isZero();
    }
  }

  @Nested
  @DisplayName("acknowledge")
  class Acknowledge {

    @Test
    void should_acknowledge_open_mismatch() {
      when(mismatchRepository.acknowledge(MISMATCH_ID, WORKSPACE_ID, USER_ID))
          .thenReturn(1);
      when(mismatchRepository.findById(MISMATCH_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(buildMismatchRow("ACKNOWLEDGED")));

      MismatchResponse result = service.acknowledge(WORKSPACE_ID, MISMATCH_ID, USER_ID);

      assertThat(result.status()).isEqualTo("ACKNOWLEDGED");
      verify(stompPublisher).publishAcknowledged(
          eq(WORKSPACE_ID), eq(MISMATCH_ID),
          any(), any(), any(), any());
    }

    @Test
    void should_throw_bad_request_when_already_resolved() {
      when(mismatchRepository.acknowledge(MISMATCH_ID, WORKSPACE_ID, USER_ID))
          .thenReturn(0);
      when(mismatchRepository.findById(MISMATCH_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(buildMismatchRow("RESOLVED")));

      assertThatThrownBy(() ->
          service.acknowledge(WORKSPACE_ID, MISMATCH_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_not_found_when_row_missing() {
      when(mismatchRepository.acknowledge(MISMATCH_ID, WORKSPACE_ID, USER_ID))
          .thenReturn(0);
      when(mismatchRepository.findById(MISMATCH_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() ->
          service.acknowledge(WORKSPACE_ID, MISMATCH_ID, USER_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("resolve")
  class Resolve {

    @Test
    void should_resolve_mismatch() {
      when(mismatchRepository.resolve(MISMATCH_ID, WORKSPACE_ID,
          "ACCEPTED", "ok", USER_ID))
          .thenReturn(1);
      when(mismatchRepository.findById(MISMATCH_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(buildMismatchRow("RESOLVED")));

      MismatchResponse result = service.resolve(
          WORKSPACE_ID, MISMATCH_ID, "ACCEPTED", "ok", USER_ID);

      assertThat(result.status()).isEqualTo("RESOLVED");
      verify(stompPublisher).publishResolved(
          eq(WORKSPACE_ID), eq(MISMATCH_ID),
          any(), any(), any(), any());
    }

    @Test
    void should_publish_ignored_event_for_ignored_resolution() {
      when(mismatchRepository.resolve(MISMATCH_ID, WORKSPACE_ID,
          "IGNORED", "not relevant", USER_ID))
          .thenReturn(1);
      var row = buildMismatchRow("RESOLVED");
      row.setResolvedReason("IGNORED");
      when(mismatchRepository.findById(MISMATCH_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(row));

      service.resolve(WORKSPACE_ID, MISMATCH_ID, "IGNORED", "not relevant", USER_ID);

      verify(stompPublisher).publishIgnored(
          eq(WORKSPACE_ID), eq(MISMATCH_ID),
          any(), any(), any(), any());
    }

    @Test
    void should_throw_for_invalid_resolution_type() {
      assertThatThrownBy(() ->
          service.resolve(WORKSPACE_ID, MISMATCH_ID, "INVALID_TYPE", null, USER_ID))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_bad_request_when_already_resolved() {
      when(mismatchRepository.resolve(MISMATCH_ID, WORKSPACE_ID,
          "REPRICED", null, USER_ID))
          .thenReturn(0);
      when(mismatchRepository.findById(MISMATCH_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(buildMismatchRow("RESOLVED")));

      assertThatThrownBy(() ->
          service.resolve(WORKSPACE_ID, MISMATCH_ID, "REPRICED", null, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_not_found_when_row_missing_on_resolve() {
      when(mismatchRepository.resolve(MISMATCH_ID, WORKSPACE_ID,
          "INVESTIGATED", null, USER_ID))
          .thenReturn(0);
      when(mismatchRepository.findById(MISMATCH_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() ->
          service.resolve(WORKSPACE_ID, MISMATCH_ID, "INVESTIGATED", null, USER_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  private MismatchRow buildMismatchRow(String status) {
    return MismatchRow.builder()
        .alertEventId(MISMATCH_ID)
        .mismatchType("PRICE")
        .offerId(1L)
        .offerName("Test Product")
        .skuCode("SKU-001")
        .expectedValue("1000")
        .actualValue("900")
        .deltaPct(new BigDecimal("10"))
        .severity("WARNING")
        .status(status)
        .resolvedReason(null)
        .detectedAt(OffsetDateTime.now())
        .connectionName("WB Connection")
        .marketplaceType("WB")
        .resolvedAt(null)
        .relatedActionId(null)
        .build();
  }
}
