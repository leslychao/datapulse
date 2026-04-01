package io.datapulse.promotions.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionRepository;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import io.datapulse.promotions.persistence.PromoDecisionRepository;
import io.datapulse.promotions.persistence.PromoEvaluationEntity;
import io.datapulse.promotions.persistence.PromoEvaluationRepository;
import io.datapulse.promotions.persistence.PromoEvaluationRunEntity;
import io.datapulse.promotions.persistence.PromoEvaluationRunRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import io.datapulse.promotions.persistence.PromoProductRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoEvaluationServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long CONNECTION_ID = 5L;
  private static final long RUN_ID = 10L;

  @Mock
  private PromoEvaluationRunRepository runRepository;
  @Mock
  private PromoEvaluationRepository evaluationRepository;
  @Mock
  private PromoDecisionRepository decisionRepository;
  @Mock
  private PromoActionRepository actionRepository;
  @Mock
  private PromoPolicyResolver policyResolver;
  @Mock
  private ObjectMapper objectMapper;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Captor
  private ArgumentCaptor<List<PromoEvaluationEntity>> evalCaptor;
  @Captor
  private ArgumentCaptor<List<PromoDecisionEntity>> decisionCaptor;

  @InjectMocks
  private PromoEvaluationService service;

  @Nested
  @DisplayName("executeRun")
  class ExecuteRun {

    @Test
    void should_skip_when_run_is_not_pending() {
      var run = buildRun(PromoRunStatus.IN_PROGRESS);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      service.executeRun(RUN_ID);

      verify(policyResolver, never()).loadEligibleProducts(anyLong(), anyLong());
    }

    @Test
    void should_complete_with_zero_counters_when_no_products() {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of());

      service.executeRun(RUN_ID);

      assertThat(run.getStatus()).isEqualTo(PromoRunStatus.COMPLETED);
      assertThat(run.getParticipateCount()).isZero();
      assertThat(run.getDeclineCount()).isZero();
      verify(eventPublisher).publishEvent(any(PromoEvaluationCompletedEvent.class));
    }

    @Test
    void should_evaluate_profitable_eligible_product_as_participate() throws Exception {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      var product = buildProduct("ELIGIBLE",
          new BigDecimal("500"), new BigDecimal("700"),
          new BigDecimal("200"), 100);
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of(product));

      var policy = buildPolicy(new BigDecimal("10"), ParticipationMode.FULL_AUTO);
      when(policyResolver.resolvePolicy(anyLong(), any(), eq(CONNECTION_ID), eq(WORKSPACE_ID)))
          .thenReturn(policy);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.executeRun(RUN_ID);

      assertThat(run.getStatus()).isEqualTo(PromoRunStatus.COMPLETED);
      assertThat(run.getParticipateCount()).isEqualTo(1);
      verify(evaluationRepository).saveAll(evalCaptor.capture());
      assertThat(evalCaptor.getValue()).hasSize(1);
      assertThat(evalCaptor.getValue().get(0).getEvaluationResult())
          .isEqualTo(PromoEvaluationResult.PROFITABLE);
    }

    @Test
    void should_evaluate_unprofitable_product_as_decline() throws Exception {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      var product = buildProduct("ELIGIBLE",
          new BigDecimal("100"), new BigDecimal("700"),
          new BigDecimal("300"), 100);
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of(product));

      var policy = buildPolicy(new BigDecimal("50"), ParticipationMode.FULL_AUTO);
      when(policyResolver.resolvePolicy(anyLong(), any(), eq(CONNECTION_ID), eq(WORKSPACE_ID)))
          .thenReturn(policy);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.executeRun(RUN_ID);

      assertThat(run.getDeclineCount()).isEqualTo(1);
    }

    @Test
    void should_classify_insufficient_data_when_cogs_null() throws Exception {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      var product = new PromoProductRow(
          1L, 1L, 1L, null, "ELIGIBLE",
          new BigDecimal("500"), new BigDecimal("700"),
          null, null, 100, null,
          OffsetDateTime.now(), OffsetDateTime.now().plusDays(14), null);
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of(product));

      var policy = buildPolicy(new BigDecimal("10"), ParticipationMode.FULL_AUTO);
      when(policyResolver.resolvePolicy(anyLong(), any(), eq(CONNECTION_ID), eq(WORKSPACE_ID)))
          .thenReturn(policy);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.executeRun(RUN_ID);

      verify(evaluationRepository).saveAll(evalCaptor.capture());
      assertThat(evalCaptor.getValue().get(0).getEvaluationResult())
          .isEqualTo(PromoEvaluationResult.INSUFFICIENT_DATA);
    }

    @Test
    void should_skip_product_when_no_policy_resolved() {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      var product = buildProduct("ELIGIBLE",
          new BigDecimal("500"), new BigDecimal("700"),
          new BigDecimal("200"), 100);
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of(product));
      when(policyResolver.resolvePolicy(anyLong(), any(), eq(CONNECTION_ID), eq(WORKSPACE_ID)))
          .thenReturn(null);

      service.executeRun(RUN_ID);

      assertThat(run.getStatus()).isEqualTo(PromoRunStatus.COMPLETED);
      verify(evaluationRepository).saveAll(evalCaptor.capture());
      assertThat(evalCaptor.getValue()).isEmpty();
    }

    @Test
    void should_set_failed_status_when_exception() throws Exception {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenThrow(new RuntimeException("DB error"));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.executeRun(RUN_ID);

      assertThat(run.getStatus()).isEqualTo(PromoRunStatus.FAILED);
      assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void should_not_create_action_for_recommendation_mode() throws Exception {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      var product = buildProduct("ELIGIBLE",
          new BigDecimal("500"), new BigDecimal("700"),
          new BigDecimal("200"), 100);
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of(product));

      var policy = buildPolicy(new BigDecimal("10"), ParticipationMode.RECOMMENDATION);
      when(policyResolver.resolvePolicy(anyLong(), any(), eq(CONNECTION_ID), eq(WORKSPACE_ID)))
          .thenReturn(policy);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.executeRun(RUN_ID);

      verify(actionRepository, never()).save(any());
    }

    @Test
    void should_create_pending_approval_action_for_semi_auto() throws Exception {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      var product = buildProduct("ELIGIBLE",
          new BigDecimal("500"), new BigDecimal("700"),
          new BigDecimal("200"), 100);
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of(product));

      var policy = buildPolicy(new BigDecimal("10"), ParticipationMode.SEMI_AUTO);
      when(policyResolver.resolvePolicy(anyLong(), any(), eq(CONNECTION_ID), eq(WORKSPACE_ID)))
          .thenReturn(policy);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.executeRun(RUN_ID);

      verify(actionRepository).save(any(PromoActionEntity.class));
    }

    @Test
    void should_deactivate_unprofitable_participating_product() throws Exception {
      var run = buildRun(PromoRunStatus.PENDING);
      when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

      var product = buildProduct("PARTICIPATING",
          new BigDecimal("100"), new BigDecimal("700"),
          new BigDecimal("300"), 100);
      when(policyResolver.loadEligibleProducts(CONNECTION_ID, WORKSPACE_ID))
          .thenReturn(List.of(product));

      var policy = buildPolicy(new BigDecimal("50"), ParticipationMode.FULL_AUTO);
      when(policyResolver.resolvePolicy(anyLong(), any(), eq(CONNECTION_ID), eq(WORKSPACE_ID)))
          .thenReturn(policy);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      service.executeRun(RUN_ID);

      assertThat(run.getDeactivateCount()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("PromoActionStatus")
  class ActionStatusTest {

    @Test
    void should_identify_terminal_statuses() {
      assertThat(PromoActionStatus.SUCCEEDED.isTerminal()).isTrue();
      assertThat(PromoActionStatus.FAILED.isTerminal()).isTrue();
      assertThat(PromoActionStatus.EXPIRED.isTerminal()).isTrue();
      assertThat(PromoActionStatus.CANCELLED.isTerminal()).isTrue();
      assertThat(PromoActionStatus.PENDING_APPROVAL.isTerminal()).isFalse();
      assertThat(PromoActionStatus.APPROVED.isTerminal()).isFalse();
      assertThat(PromoActionStatus.EXECUTING.isTerminal()).isFalse();
    }

    @Test
    void should_identify_cancellable_statuses() {
      assertThat(PromoActionStatus.PENDING_APPROVAL.isCancellable()).isTrue();
      assertThat(PromoActionStatus.APPROVED.isCancellable()).isTrue();
      assertThat(PromoActionStatus.EXECUTING.isCancellable()).isFalse();
      assertThat(PromoActionStatus.SUCCEEDED.isCancellable()).isFalse();
    }
  }

  private PromoEvaluationRunEntity buildRun(PromoRunStatus status) {
    var run = new PromoEvaluationRunEntity();
    run.setId(RUN_ID);
    run.setWorkspaceId(WORKSPACE_ID);
    run.setConnectionId(CONNECTION_ID);
    run.setStatus(status);
    run.setTriggerType(PromoRunTriggerType.MANUAL);
    return run;
  }

  private PromoProductRow buildProduct(String status, BigDecimal promoPrice,
                                        BigDecimal regularPrice, BigDecimal cogs,
                                        int stock) {
    return new PromoProductRow(
        1L, 1L, 1L, 10L, status,
        promoPrice, regularPrice, cogs,
        null, stock, new BigDecimal("5"),
        OffsetDateTime.now(), OffsetDateTime.now().plusDays(14), null);
  }

  private PromoPolicyEntity buildPolicy(BigDecimal minMargin, ParticipationMode mode) {
    var p = new PromoPolicyEntity();
    p.setId(1L);
    p.setWorkspaceId(WORKSPACE_ID);
    p.setStatus(PromoPolicyStatus.ACTIVE);
    p.setParticipationMode(mode);
    p.setMinMarginPct(minMargin);
    p.setMinStockDaysOfCover(7);
    p.setVersion(1);
    p.setCreatedBy(1L);
    p.setName("Test");
    return p;
  }
}
