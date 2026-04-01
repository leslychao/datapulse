package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.PriceJournalEntryResponse;
import io.datapulse.sellerops.persistence.PriceJournalJdbcRepository;
import io.datapulse.sellerops.persistence.PriceJournalRow;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceJournalServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long OFFER_ID = 100L;

  @Mock
  private PriceJournalJdbcRepository journalRepository;

  @InjectMocks
  private PriceJournalService service;

  @Nested
  @DisplayName("getJournal")
  class GetJournal {

    @Test
    void should_return_mapped_page() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PriceJournalRow.builder()
          .decisionId(1L)
          .decisionDate(OffsetDateTime.now())
          .decisionType("CHANGE")
          .policyName("Target Margin")
          .policyVersion(3)
          .currentPrice(new BigDecimal("1000"))
          .targetPrice(new BigDecimal("1100"))
          .priceChangePct(new BigDecimal("10.00"))
          .actionStatus("SUCCEEDED")
          .executionMode("LIVE")
          .actualPrice(new BigDecimal("1100"))
          .reconciliationSource("API_CONFIRM")
          .explanationSummary("Margin below target")
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getContent()).hasSize(1);
      PriceJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.decisionId()).isEqualTo(1L);
      assertThat(entry.decisionType()).isEqualTo("CHANGE");
      assertThat(entry.policyName()).isEqualTo("Target Margin");
      assertThat(entry.currentPrice()).isEqualTo(new BigDecimal("1000"));
      assertThat(entry.targetPrice()).isEqualTo(new BigDecimal("1100"));
      assertThat(entry.actionStatus()).isEqualTo("SUCCEEDED");
      assertThat(entry.actualPrice()).isEqualTo(new BigDecimal("1100"));
    }

    @Test
    void should_return_empty_page_when_no_entries() {
      Pageable pageable = PageRequest.of(0, 20);
      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(Page.empty(pageable));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getContent()).isEmpty();
      assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void should_pass_filters_to_repository() {
      Pageable pageable = PageRequest.of(0, 10);
      LocalDate from = LocalDate.of(2025, 1, 1);
      LocalDate to = LocalDate.of(2025, 12, 31);

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, from, to, "SKIP", "FAILED", pageable))
          .thenReturn(Page.empty(pageable));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, from, to, "SKIP", "FAILED", pageable);

      assertThat(result.getContent()).isEmpty();
    }

    @Test
    void should_map_skip_reason_correctly() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PriceJournalRow.builder()
          .decisionId(2L)
          .decisionDate(OffsetDateTime.now())
          .decisionType("SKIP")
          .skipReason("pricing.guard.manual_lock.blocked")
          .policyName("Target Margin")
          .policyVersion(1)
          .currentPrice(new BigDecimal("1000"))
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      PriceJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.decisionType()).isEqualTo("SKIP");
      assertThat(entry.skipReason()).isEqualTo("pricing.guard.manual_lock.blocked");
      assertThat(entry.targetPrice()).isNull();
      assertThat(entry.actionStatus()).isNull();
    }

    @Test
    void should_map_multiple_entries_preserving_order() {
      Pageable pageable = PageRequest.of(0, 20);
      var row1 = PriceJournalRow.builder()
          .decisionId(1L)
          .decisionDate(OffsetDateTime.now().minusDays(2))
          .decisionType("CHANGE")
          .policyName("Corridor")
          .policyVersion(1)
          .currentPrice(new BigDecimal("800"))
          .targetPrice(new BigDecimal("850"))
          .build();
      var row2 = PriceJournalRow.builder()
          .decisionId(2L)
          .decisionDate(OffsetDateTime.now().minusDays(1))
          .decisionType("SKIP")
          .policyName("Target Margin")
          .policyVersion(2)
          .currentPrice(new BigDecimal("850"))
          .build();
      var row3 = PriceJournalRow.builder()
          .decisionId(3L)
          .decisionDate(OffsetDateTime.now())
          .decisionType("CHANGE")
          .policyName("Target Margin")
          .policyVersion(2)
          .currentPrice(new BigDecimal("850"))
          .targetPrice(new BigDecimal("900"))
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row1, row2, row3), pageable, 3));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getContent()).hasSize(3);
      assertThat(result.getContent().get(0).decisionId()).isEqualTo(1L);
      assertThat(result.getContent().get(1).decisionId()).isEqualTo(2L);
      assertThat(result.getContent().get(2).decisionId()).isEqualTo(3L);
    }

    @Test
    void should_map_hold_decision_without_target_or_action() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PriceJournalRow.builder()
          .decisionId(5L)
          .decisionDate(OffsetDateTime.now())
          .decisionType("HOLD")
          .policyName("Target Margin")
          .policyVersion(1)
          .currentPrice(new BigDecimal("1000"))
          .explanationSummary("Guard blocked: volatility")
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      PriceJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.decisionType()).isEqualTo("HOLD");
      assertThat(entry.targetPrice()).isNull();
      assertThat(entry.actionStatus()).isNull();
      assertThat(entry.explanationSummary()).isEqualTo("Guard blocked: volatility");
    }

    @Test
    void should_preserve_pagination_metadata() {
      Pageable pageable = PageRequest.of(2, 10);
      var row = PriceJournalRow.builder()
          .decisionId(1L)
          .decisionDate(OffsetDateTime.now())
          .decisionType("CHANGE")
          .policyName("Corridor")
          .policyVersion(1)
          .currentPrice(new BigDecimal("500"))
          .targetPrice(new BigDecimal("550"))
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 30));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getTotalElements()).isEqualTo(30);
      assertThat(result.getNumber()).isEqualTo(2);
      assertThat(result.getSize()).isEqualTo(10);
      assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    void should_map_reconciliation_source_and_actual_price() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PriceJournalRow.builder()
          .decisionId(10L)
          .decisionDate(OffsetDateTime.now())
          .decisionType("CHANGE")
          .policyName("Target Margin")
          .policyVersion(1)
          .currentPrice(new BigDecimal("1000"))
          .targetPrice(new BigDecimal("1100"))
          .priceChangePct(new BigDecimal("10.00"))
          .actionStatus("SUCCEEDED")
          .executionMode("LIVE")
          .actualPrice(new BigDecimal("1100"))
          .reconciliationSource("PRICE_SNAPSHOT")
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      PriceJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.actualPrice()).isEqualByComparingTo(new BigDecimal("1100"));
      assertThat(entry.reconciliationSource()).isEqualTo("PRICE_SNAPSHOT");
    }

    @Test
    void should_map_null_explanation_summary() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PriceJournalRow.builder()
          .decisionId(11L)
          .decisionDate(OffsetDateTime.now())
          .decisionType("CHANGE")
          .policyName("Corridor")
          .policyVersion(1)
          .currentPrice(new BigDecimal("500"))
          .targetPrice(new BigDecimal("520"))
          .explanationSummary(null)
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PriceJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getContent().get(0).explanationSummary()).isNull();
    }
  }
}
