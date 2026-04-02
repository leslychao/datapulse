package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.platform.audit.AutomationBlockerChecker;
import io.datapulse.pricing.api.BulkManualApplyResponse;
import io.datapulse.pricing.api.BulkManualPreviewRequest;
import io.datapulse.pricing.api.BulkManualPreviewRequest.PriceChange;
import io.datapulse.pricing.api.BulkManualPreviewResponse;
import io.datapulse.pricing.domain.PricingConstraintResolver.ConstraintResolution;
import io.datapulse.pricing.domain.guard.PricingGuardChain;
import io.datapulse.pricing.domain.guard.PricingGuardChain.GuardChainResult;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository.EnrichedOfferRow;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunRepository;

@ExtendWith(MockitoExtension.class)
class BulkManualPricingServiceTest {

  @Mock private AutomationBlockerChecker automationBlockerChecker;
  @Mock private PricingDataReadRepository dataReadRepository;
  @Mock private PricingRunRepository runRepository;
  @Mock private PriceDecisionRepository decisionRepository;
  @Mock private PricingConstraintResolver constraintResolver;
  @Mock private PricingGuardChain guardChain;
  @Mock private PricingActionScheduler actionScheduler;
  @Mock private ExplanationBuilder explanationBuilder;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks
  private BulkManualPricingService service;

  @Captor
  private ArgumentCaptor<List<PriceDecisionEntity>> decisionsCaptor;

  private static final long WORKSPACE_ID = 10L;

  @Nested
  @DisplayName("preview")
  class Preview {

    @Test
    @DisplayName("returns CHANGE preview when price differs and guards pass")
    void should_returnChange_when_priceChanges() {
      BulkManualPreviewRequest request = previewRequest(100L, "1200");

      EnrichedOfferRow offer = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(List.of(100L), WORKSPACE_ID))
          .thenReturn(List.of(offer));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new ConstraintResolution(new BigDecimal("1200"), List.of()));

      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      BulkManualPreviewResponse response = service.preview(request, WORKSPACE_ID);

      assertThat(response.offers()).hasSize(1);
      assertThat(response.offers().get(0).result()).isEqualTo("CHANGE");
      assertThat(response.offers().get(0).effectivePrice())
          .isEqualByComparingTo(new BigDecimal("1200"));
      assertThat(response.summary().willChange()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns SKIP when offer not found")
    void should_returnSkip_when_offerNotFound() {
      BulkManualPreviewRequest request = previewRequest(999L, "1200");

      when(dataReadRepository.findOffersByIds(List.of(999L), WORKSPACE_ID))
          .thenReturn(List.of());

      BulkManualPreviewResponse response = service.preview(request, WORKSPACE_ID);

      assertThat(response.offers()).hasSize(1);
      assertThat(response.offers().get(0).result()).isEqualTo("SKIP");
      assertThat(response.summary().willSkip()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns SKIP when guard blocks the change")
    void should_returnSkip_when_guardBlocks() {
      BulkManualPreviewRequest request = previewRequest(100L, "1200");

      EnrichedOfferRow offer = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(List.of(100L), WORKSPACE_ID))
          .thenReturn(List.of(offer));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new ConstraintResolution(new BigDecimal("1200"), List.of()));

      GuardResult blocking = GuardResult.block("manual_lock_guard", "pricing.guard.manual_lock");
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(false, blocking, List.of()));

      BulkManualPreviewResponse response = service.preview(request, WORKSPACE_ID);

      assertThat(response.offers().get(0).result()).isEqualTo("SKIP");
      assertThat(response.summary().willBlock()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns SKIP when effective price equals current (no change)")
    void should_returnSkip_when_noChange() {
      BulkManualPreviewRequest request = previewRequest(100L, "1000");

      EnrichedOfferRow offer = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(List.of(100L), WORKSPACE_ID))
          .thenReturn(List.of(offer));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new ConstraintResolution(new BigDecimal("1000"), List.of()));

      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      BulkManualPreviewResponse response = service.preview(request, WORKSPACE_ID);

      assertThat(response.offers().get(0).result()).isEqualTo("SKIP");
      assertThat(response.summary().willSkip()).isEqualTo(1);
    }

    @Test
    @DisplayName("summary computes correct avg and max change percentages")
    void should_computeSummaryStats_when_multipleChanges() {
      List<PriceChange> changes = List.of(
          new PriceChange(100L, new BigDecimal("1100")),
          new PriceChange(200L, new BigDecimal("1300")));
      BulkManualPreviewRequest request = new BulkManualPreviewRequest(changes);

      EnrichedOfferRow offer1 = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("400"));
      EnrichedOfferRow offer2 = enrichedOffer(200L, new BigDecimal("1000"), new BigDecimal("400"));
      when(dataReadRepository.findOffersByIds(any(), eq(WORKSPACE_ID)))
          .thenReturn(List.of(offer1, offer2));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenAnswer(i -> new ConstraintResolution(i.getArgument(0), List.of()));
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      BulkManualPreviewResponse response = service.preview(request, WORKSPACE_ID);

      assertThat(response.summary().willChange()).isEqualTo(2);
      assertThat(response.summary().maxChangePct())
          .isGreaterThanOrEqualTo(new BigDecimal("30"));
      assertThat(response.summary().minMarginAfter()).isNotNull();
    }
  }

  @Nested
  @DisplayName("apply")
  class Apply {

    @Test
    @DisplayName("creates decisions and schedules actions for valid changes")
    void should_createDecisionsAndActions_when_validChanges() throws Exception {
      BulkManualPreviewRequest request = previewRequest(100L, "1200");
      setupApplyMocks(request);

      EnrichedOfferRow offer = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(any(), eq(WORKSPACE_ID)))
          .thenReturn(List.of(offer));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new ConstraintResolution(new BigDecimal("1200"), List.of()));
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      BulkManualApplyResponse response = service.apply(request, WORKSPACE_ID, 1L);

      assertThat(response.processed()).isEqualTo(1);
      assertThat(response.skipped()).isZero();
      verify(decisionRepository).saveAll(decisionsCaptor.capture());
      assertThat(decisionsCaptor.getValue()).hasSize(1);
      verify(actionScheduler).scheduleAction(
          anyLong(), eq(100L), any(), eq(ExecutionMode.FULL_AUTO), eq(WORKSPACE_ID));
    }

    @Test
    @DisplayName("throws BadRequest when duplicate request detected")
    void should_throwBadRequest_when_duplicateRequest() {
      BulkManualPreviewRequest request = previewRequest(100L, "1200");

      when(runRepository.existsByRequestHashAndTriggerTypeAndStatusNotIn(
          any(), eq(RunTriggerType.MANUAL_BULK), any())).thenReturn(true);

      assertThatThrownBy(() -> service.apply(request, WORKSPACE_ID, 1L))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("throws BadRequest when automation is blocked")
    void should_throwBadRequest_when_automationBlocked() throws Exception {
      BulkManualPreviewRequest request = previewRequest(100L, "1200");

      when(runRepository.existsByRequestHashAndTriggerTypeAndStatusNotIn(
          any(), eq(RunTriggerType.MANUAL_BULK), any())).thenReturn(false);

      EnrichedOfferRow offer = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(any(), eq(WORKSPACE_ID)))
          .thenReturn(List.of(offer));

      when(automationBlockerChecker.isBlocked(WORKSPACE_ID, 20L)).thenReturn(true);

      assertThatThrownBy(() -> service.apply(request, WORKSPACE_ID, 1L))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("skips offer when not found in DB")
    void should_skip_when_offerNotFound() throws Exception {
      BulkManualPreviewRequest request = previewRequest(999L, "1200");
      setupApplyMocks(request);

      when(dataReadRepository.findOffersByIds(any(), eq(WORKSPACE_ID)))
          .thenReturn(List.of());
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      BulkManualApplyResponse response = service.apply(request, WORKSPACE_ID, 1L);

      assertThat(response.skipped()).isEqualTo(1);
      assertThat(response.processed()).isZero();
      verify(decisionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("skips offer when guard blocks the change")
    void should_skip_when_guardBlocksInApply() throws Exception {
      BulkManualPreviewRequest request = previewRequest(100L, "1200");
      setupApplyMocks(request);

      EnrichedOfferRow offer = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(any(), eq(WORKSPACE_ID)))
          .thenReturn(List.of(offer));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new ConstraintResolution(new BigDecimal("1200"), List.of()));

      GuardResult blocking = GuardResult.block("manual_lock_guard", "pricing.guard.manual_lock");
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(false, blocking, List.of()));
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      BulkManualApplyResponse response = service.apply(request, WORKSPACE_ID, 1L);

      assertThat(response.skipped()).isEqualTo(1);
      assertThat(response.processed()).isZero();
    }

    @Test
    @DisplayName("catches per-offer exception and continues processing")
    void should_catchException_when_singleOfferFails() throws Exception {
      List<PriceChange> changes = List.of(
          new PriceChange(100L, new BigDecimal("1200")),
          new PriceChange(200L, new BigDecimal("1300")));
      BulkManualPreviewRequest request = new BulkManualPreviewRequest(changes);
      setupApplyMocks(request);

      EnrichedOfferRow offer1 = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      EnrichedOfferRow offer2 = enrichedOffer(200L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(any(), eq(WORKSPACE_ID)))
          .thenReturn(List.of(offer1, offer2));

      when(constraintResolver.resolve(eq(new BigDecimal("1200")), any(), any()))
          .thenThrow(new RuntimeException("constraint error"));
      when(constraintResolver.resolve(eq(new BigDecimal("1300")), any(), any()))
          .thenReturn(new ConstraintResolution(new BigDecimal("1300"), List.of()));
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      BulkManualApplyResponse response = service.apply(request, WORKSPACE_ID, 1L);

      assertThat(response.processed()).isEqualTo(1);
      assertThat(response.errored()).isEqualTo(1);
      assertThat(response.skipped()).isZero();
      assertThat(response.errors()).hasSize(1);
    }

    @Test
    @DisplayName("decision entity has MANUAL_OVERRIDE strategy type and FULL_AUTO mode")
    void should_setManualOverrideType_when_decisionCreated() throws Exception {
      BulkManualPreviewRequest request = previewRequest(100L, "1200");
      setupApplyMocks(request);

      EnrichedOfferRow offer = enrichedOffer(100L, new BigDecimal("1000"), new BigDecimal("500"));
      when(dataReadRepository.findOffersByIds(any(), eq(WORKSPACE_ID)))
          .thenReturn(List.of(offer));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new ConstraintResolution(new BigDecimal("1200"), List.of()));
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.apply(request, WORKSPACE_ID, 1L);

      verify(decisionRepository).saveAll(decisionsCaptor.capture());
      PriceDecisionEntity decision = decisionsCaptor.getValue().get(0);
      assertThat(decision.getStrategyType()).isEqualTo(PolicyType.MANUAL_OVERRIDE);
      assertThat(decision.getDecisionType()).isEqualTo(DecisionType.CHANGE);
      assertThat(decision.getExecutionMode()).isEqualTo("LIVE");
    }
  }

  private BulkManualPreviewRequest previewRequest(long offerId, String price) {
    return new BulkManualPreviewRequest(
        List.of(new PriceChange(offerId, new BigDecimal(price))));
  }

  private void setupApplyMocks(BulkManualPreviewRequest request) {
    when(runRepository.existsByRequestHashAndTriggerTypeAndStatusNotIn(
        any(), eq(RunTriggerType.MANUAL_BULK), any())).thenReturn(false);
    when(automationBlockerChecker.isBlocked(anyLong(), anyLong())).thenReturn(false);
    when(runRepository.save(any())).thenAnswer(i -> {
      PricingRunEntity e = i.getArgument(0);
      e.setId(1L);
      return e;
    });
    lenient().when(decisionRepository.saveAll(any())).thenAnswer(i -> {
      List<PriceDecisionEntity> entities = i.getArgument(0);
      long idCounter = 100L;
      for (PriceDecisionEntity e : entities) {
        e.setId(idCounter++);
      }
      return entities;
    });
  }

  private EnrichedOfferRow enrichedOffer(long id, BigDecimal currentPrice, BigDecimal cogs) {
    return new EnrichedOfferRow(
        id, 1L, 5L, 20L, "ACTIVE", "SKU-" + id, "Product " + id,
        currentPrice, cogs);
  }
}
