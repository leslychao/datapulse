package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.PromoJournalEntryResponse;
import io.datapulse.sellerops.persistence.PromoJournalJdbcRepository;
import io.datapulse.sellerops.persistence.PromoJournalRow;
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
class PromoJournalServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long OFFER_ID = 100L;

  @Mock
  private PromoJournalJdbcRepository journalRepository;

  @InjectMocks
  private PromoJournalService service;

  @Nested
  @DisplayName("getJournal")
  class GetJournal {

    @Test
    void should_return_mapped_page() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PromoJournalRow.builder()
          .decisionId(1L)
          .decisionDate(OffsetDateTime.now())
          .promoName("Spring Sale")
          .promoType("SALE")
          .periodFrom(LocalDate.of(2025, 3, 1))
          .periodTo(LocalDate.of(2025, 3, 31))
          .evaluationResult("PROFITABLE")
          .participationDecision("PARTICIPATE")
          .actionStatus("SUCCEEDED")
          .requiredPrice(new BigDecimal("850"))
          .marginAtPromoPrice(new BigDecimal("25.00"))
          .marginDeltaPct(new BigDecimal("-5.00"))
          .explanationSummary("Margin acceptable")
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getContent()).hasSize(1);
      PromoJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.decisionId()).isEqualTo(1L);
      assertThat(entry.promoName()).isEqualTo("Spring Sale");
      assertThat(entry.promoType()).isEqualTo("SALE");
      assertThat(entry.evaluationResult()).isEqualTo("PROFITABLE");
      assertThat(entry.participationDecision()).isEqualTo("PARTICIPATE");
      assertThat(entry.requiredPrice()).isEqualTo(new BigDecimal("850"));
      assertThat(entry.marginAtPromoPrice()).isEqualTo(new BigDecimal("25.00"));
    }

    @Test
    void should_return_empty_page_when_no_entries() {
      Pageable pageable = PageRequest.of(0, 20);
      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(Page.empty(pageable));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getContent()).isEmpty();
      assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void should_pass_date_filters_to_repository() {
      Pageable pageable = PageRequest.of(0, 10);
      LocalDate from = LocalDate.of(2025, 1, 1);
      LocalDate to = LocalDate.of(2025, 6, 30);

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, from, to, null, null, pageable))
          .thenReturn(Page.empty(pageable));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, from, to, null, null, pageable);

      assertThat(result.getContent()).isEmpty();
    }

    @Test
    void should_map_declined_promo_correctly() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PromoJournalRow.builder()
          .decisionId(2L)
          .decisionDate(OffsetDateTime.now())
          .promoName("Flash Sale")
          .promoType("FLASH")
          .periodFrom(LocalDate.of(2025, 4, 1))
          .periodTo(LocalDate.of(2025, 4, 3))
          .evaluationResult("UNPROFITABLE")
          .participationDecision("DECLINE")
          .requiredPrice(new BigDecimal("300"))
          .marginAtPromoPrice(new BigDecimal("-5.00"))
          .marginDeltaPct(new BigDecimal("-35.00"))
          .explanationSummary("Below min margin")
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      PromoJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.participationDecision()).isEqualTo("DECLINE");
      assertThat(entry.evaluationResult()).isEqualTo("UNPROFITABLE");
      assertThat(entry.actionStatus()).isNull();
    }

    @Test
    void should_map_pending_review_decision() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PromoJournalRow.builder()
          .decisionId(3L)
          .decisionDate(OffsetDateTime.now())
          .promoName("Black Friday")
          .promoType("SALE")
          .periodFrom(LocalDate.of(2025, 11, 25))
          .periodTo(LocalDate.of(2025, 11, 30))
          .evaluationResult("MARGINAL")
          .participationDecision("PENDING_REVIEW")
          .requiredPrice(new BigDecimal("500"))
          .marginAtPromoPrice(new BigDecimal("5.00"))
          .marginDeltaPct(new BigDecimal("-15.00"))
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      PromoJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.participationDecision()).isEqualTo("PENDING_REVIEW");
      assertThat(entry.evaluationResult()).isEqualTo("MARGINAL");
    }

    @Test
    void should_map_multiple_entries_preserving_order() {
      Pageable pageable = PageRequest.of(0, 20);
      var row1 = PromoJournalRow.builder()
          .decisionId(1L)
          .decisionDate(OffsetDateTime.now().minusDays(5))
          .promoName("Spring Sale")
          .periodFrom(LocalDate.of(2025, 3, 1))
          .periodTo(LocalDate.of(2025, 3, 31))
          .participationDecision("PARTICIPATE")
          .build();
      var row2 = PromoJournalRow.builder()
          .decisionId(2L)
          .decisionDate(OffsetDateTime.now())
          .promoName("Flash Sale")
          .periodFrom(LocalDate.of(2025, 4, 1))
          .periodTo(LocalDate.of(2025, 4, 3))
          .participationDecision("DECLINE")
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row1, row2), pageable, 2));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getContent().get(0).decisionId()).isEqualTo(1L);
      assertThat(result.getContent().get(1).decisionId()).isEqualTo(2L);
    }

    @Test
    void should_map_null_optional_fields() {
      Pageable pageable = PageRequest.of(0, 20);
      var row = PromoJournalRow.builder()
          .decisionId(4L)
          .decisionDate(OffsetDateTime.now())
          .promoName("Basic Promo")
          .promoType(null)
          .periodFrom(LocalDate.of(2025, 5, 1))
          .periodTo(LocalDate.of(2025, 5, 15))
          .participationDecision("PARTICIPATE")
          .evaluationResult("PROFITABLE")
          .actionStatus("SUCCEEDED")
          .requiredPrice(new BigDecimal("600"))
          .marginAtPromoPrice(null)
          .marginDeltaPct(null)
          .explanationSummary(null)
          .build();

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, null, null, null, null, pageable);

      PromoJournalEntryResponse entry = result.getContent().get(0);
      assertThat(entry.promoType()).isNull();
      assertThat(entry.marginAtPromoPrice()).isNull();
      assertThat(entry.marginDeltaPct()).isNull();
      assertThat(entry.explanationSummary()).isNull();
    }

    @Test
    void should_pass_all_filters_to_repository() {
      Pageable pageable = PageRequest.of(0, 10);
      LocalDate from = LocalDate.of(2025, 1, 1);
      LocalDate to = LocalDate.of(2025, 12, 31);

      when(journalRepository.findByOfferId(
          WORKSPACE_ID, OFFER_ID, from, to, "PARTICIPATE", "SUCCEEDED", pageable))
          .thenReturn(Page.empty(pageable));

      Page<PromoJournalEntryResponse> result = service.getJournal(
          WORKSPACE_ID, OFFER_ID, from, to, "PARTICIPATE", "SUCCEEDED", pageable);

      assertThat(result.getContent()).isEmpty();
    }
  }
}
