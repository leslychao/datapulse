package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.platform.audit.AutomationBlockerChecker;
import io.datapulse.pricing.domain.guard.PricingGuardChain;
import io.datapulse.pricing.domain.guard.PricingGuardChain.GuardChainResult;
import io.datapulse.pricing.domain.strategy.PricingStrategy;
import io.datapulse.pricing.domain.strategy.PricingStrategyRegistry;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository.OfferRow;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunRepository;

@ExtendWith(MockitoExtension.class)
class PricingRunServiceTest {

  @Mock private AutomationBlockerChecker automationBlockerChecker;
  @Mock private PricingDataReadRepository dataReadRepository;
  @Mock private PolicyResolver policyResolver;
  @Mock private PricingSignalCollector signalCollector;
  @Mock private PricingStrategyRegistry strategyRegistry;
  @Mock private PricingConstraintResolver constraintResolver;
  @Mock private PricingGuardChain guardChain;
  @Mock private ExplanationBuilder explanationBuilder;
  @Mock private PricingActionScheduler actionScheduler;
  @Mock private PriceDecisionRepository decisionRepository;
  @Mock private PricingRunRepository runRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Mock private PricingStrategy pricingStrategy;

  @InjectMocks
  private PricingRunService pricingRunService;

  @Captor
  private ArgumentCaptor<PricingRunCompletedEvent> eventCaptor;

  @Captor
  private ArgumentCaptor<List<PriceDecisionEntity>> decisionsCaptor;

  private PricingRunEntity runEntity;

  @BeforeEach
  void setUp() {
    runEntity = new PricingRunEntity();
    runEntity.setId(1L);
    runEntity.setWorkspaceId(10L);
    runEntity.setConnectionId(20L);
    runEntity.setStatus(RunStatus.PENDING);
  }

  @Nested
  @DisplayName("executeRun — preconditions")
  class Preconditions {

    @Test
    @DisplayName("throws when run not found")
    void should_throw_when_runNotFound() {
      when(runRepository.findById(99L)).thenReturn(Optional.empty());

      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalStateException.class,
          () -> pricingRunService.executeRun(99L));
    }

    @Test
    @DisplayName("skips when run is not PENDING")
    void should_skip_when_runNotPending() {
      runEntity.setStatus(RunStatus.COMPLETED);
      when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));

      pricingRunService.executeRun(1L);

      verify(decisionRepository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("executeRun — automation blocked")
  class AutomationBlocked {

    @Test
    @DisplayName("completes with zero counters when automation is blocked")
    void should_completeEmpty_when_automationBlocked() throws Exception {
      when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));
      when(runRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(automationBlockerChecker.isBlocked(10L, 20L)).thenReturn(true);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      pricingRunService.executeRun(1L);

      assertThat(runEntity.getStatus()).isEqualTo(RunStatus.COMPLETED);

      verify(eventPublisher).publishEvent(eventCaptor.capture());
      PricingRunCompletedEvent event = eventCaptor.getValue();
      assertThat(event.changeCount()).isZero();
      assertThat(event.skipCount()).isZero();
      assertThat(event.holdCount()).isZero();
    }
  }

  @Nested
  @DisplayName("executeRun — no offers")
  class NoOffers {

    @Test
    @DisplayName("completes with zero counters when no offers found")
    void should_completeEmpty_when_noOffers() throws Exception {
      when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));
      when(runRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(automationBlockerChecker.isBlocked(10L, 20L)).thenReturn(false);
      when(dataReadRepository.findOffersByConnection(20L)).thenReturn(List.of());
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      pricingRunService.executeRun(1L);

      assertThat(runEntity.getStatus()).isEqualTo(RunStatus.COMPLETED);
    }
  }

  @Nested
  @DisplayName("executeRun — full pipeline")
  class FullPipeline {

    @Test
    @DisplayName("produces CHANGE decision when strategy returns price, guards pass, price differs")
    void should_produceChange_when_fullPipelinePasses() throws Exception {
      setupBasePipeline();

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      StrategyResult stratResult = StrategyResult.success(
          new BigDecimal("900"), "strategy explanation");
      when(pricingStrategy.calculate(any(), any())).thenReturn(stratResult);

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new PricingConstraintResolver.ConstraintResolution(
              new BigDecimal("900"), List.of()));

      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      when(explanationBuilder.buildChange(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn("[Решение] CHANGE");

      pricingRunService.executeRun(1L);

      verify(decisionRepository).saveAll(decisionsCaptor.capture());
      List<PriceDecisionEntity> decisions = decisionsCaptor.getValue();
      assertThat(decisions).hasSize(1);
      assertThat(decisions.get(0).getDecisionType()).isEqualTo(DecisionType.CHANGE);
      assertThat(decisions.get(0).getTargetPrice())
          .isEqualByComparingTo(new BigDecimal("900"));

      assertThat(runEntity.getStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    @DisplayName("produces HOLD decision when strategy returns null price (e.g. missing COGS)")
    void should_produceHold_when_strategyReturnsNullPrice() throws Exception {
      setupBasePipeline();

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), null, 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      StrategyResult stratResult = StrategyResult.skip("COGS missing", "pricing.strategy.cogs_missing");
      when(pricingStrategy.calculate(any(), any())).thenReturn(stratResult);

      when(explanationBuilder.buildHold(any(), any())).thenReturn("[Решение] HOLD");

      pricingRunService.executeRun(1L);

      verify(decisionRepository).saveAll(decisionsCaptor.capture());
      List<PriceDecisionEntity> decisions = decisionsCaptor.getValue();
      assertThat(decisions).hasSize(1);
      assertThat(decisions.get(0).getDecisionType()).isEqualTo(DecisionType.HOLD);
      assertThat(decisions.get(0).getSkipReason()).isEqualTo("pricing.strategy.cogs_missing");
    }

    @Test
    @DisplayName("produces SKIP decision when guard blocks")
    void should_produceSkip_when_guardBlocks() throws Exception {
      setupBasePipeline();

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      StrategyResult stratResult = StrategyResult.success(
          new BigDecimal("800"), "strategy");
      when(pricingStrategy.calculate(any(), any())).thenReturn(stratResult);

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new PricingConstraintResolver.ConstraintResolution(
              new BigDecimal("800"), List.of()));

      GuardResult blocking = GuardResult.block("stale_data_guard", "pricing.guard.stale_data.stale");
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(false, blocking, List.of()));

      when(explanationBuilder.buildSkipGuard(any(), any())).thenReturn("[Решение] SKIP");

      pricingRunService.executeRun(1L);

      verify(decisionRepository).saveAll(decisionsCaptor.capture());
      PriceDecisionEntity decision = decisionsCaptor.getValue().get(0);
      assertThat(decision.getDecisionType()).isEqualTo(DecisionType.SKIP);
      assertThat(decision.getSkipReason()).isEqualTo("pricing.guard.stale_data.stale");
    }

    @Test
    @DisplayName("produces SKIP decision when target price equals current price (no change)")
    void should_produceSkip_when_targetEqualsCurrentPrice() throws Exception {
      setupBasePipeline();

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      StrategyResult stratResult = StrategyResult.success(
          new BigDecimal("1000"), "same price");
      when(pricingStrategy.calculate(any(), any())).thenReturn(stratResult);

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new PricingConstraintResolver.ConstraintResolution(
              new BigDecimal("1000"), List.of()));

      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      when(explanationBuilder.buildSkip(any(), any(), any())).thenReturn("[Решение] SKIP");

      pricingRunService.executeRun(1L);

      verify(decisionRepository).saveAll(decisionsCaptor.capture());
      PriceDecisionEntity decision = decisionsCaptor.getValue().get(0);
      assertThat(decision.getDecisionType()).isEqualTo(DecisionType.SKIP);
    }

    @Test
    @DisplayName("skips offer without signals (signal map has no entry)")
    void should_incrementSkipCount_when_signalsNull() throws Exception {
      setupBasePipeline();

      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of());

      pricingRunService.executeRun(1L);

      verify(eventPublisher).publishEvent(eventCaptor.capture());
      PricingRunCompletedEvent event = eventCaptor.getValue();
      assertThat(event.skipCount()).isEqualTo(1);
      assertThat(event.changeCount()).isZero();
    }

    @Test
    @DisplayName("filters out offers with non-ACTIVE status")
    void should_excludeOffer_when_statusNotActive() throws Exception {
      when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));
      when(runRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(automationBlockerChecker.isBlocked(10L, 20L)).thenReturn(false);
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      OfferRow inactiveOffer = new OfferRow(100L, 1L, 5L, 20L, "DISABLED");
      when(dataReadRepository.findOffersByConnection(20L)).thenReturn(List.of(inactiveOffer));

      PricePolicyEntity policy = buildPolicy();
      when(policyResolver.resolveEffectivePolicies(eq(10L), eq(20L), any()))
          .thenReturn(Map.of(100L, policy));

      pricingRunService.executeRun(1L);

      verify(decisionRepository, never()).saveAll(any());
      assertThat(runEntity.getStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    @DisplayName("filters out offers without a resolved policy")
    void should_excludeOffer_when_noPolicyResolved() throws Exception {
      when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));
      when(runRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(automationBlockerChecker.isBlocked(10L, 20L)).thenReturn(false);
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      OfferRow offer = new OfferRow(100L, 1L, 5L, 20L, "ACTIVE");
      when(dataReadRepository.findOffersByConnection(20L)).thenReturn(List.of(offer));

      when(policyResolver.resolveEffectivePolicies(eq(10L), eq(20L), any()))
          .thenReturn(Map.of());

      pricingRunService.executeRun(1L);

      verify(decisionRepository, never()).saveAll(any());
      assertThat(runEntity.getEligibleCount()).isZero();
    }
  }

  @Nested
  @DisplayName("executeRun — multiple offers with mixed outcomes")
  class MultipleOffers {

    @Test
    @DisplayName("counts CHANGE, SKIP, and HOLD correctly across multiple offers")
    void should_countCorrectly_when_mixedOutcomes() throws Exception {
      when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));
      when(runRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(automationBlockerChecker.isBlocked(10L, 20L)).thenReturn(false);
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
      when(objectMapper.readValue(eq("{}"), eq(GuardConfig.class)))
          .thenReturn(GuardConfig.DEFAULTS);
      lenient().when(decisionRepository.saveAll(any())).thenAnswer(i -> {
        List<PriceDecisionEntity> entities = i.getArgument(0);
        long idCounter = 100L;
        for (PriceDecisionEntity e : entities) {
          if (e.getId() == null) e.setId(idCounter++);
        }
        return entities;
      });

      OfferRow offer1 = new OfferRow(100L, 1L, 5L, 20L, "ACTIVE");
      OfferRow offer2 = new OfferRow(200L, 2L, 5L, 20L, "ACTIVE");
      OfferRow offer3 = new OfferRow(300L, 3L, 5L, 20L, "ACTIVE");
      when(dataReadRepository.findOffersByConnection(20L))
          .thenReturn(List.of(offer1, offer2, offer3));

      PricePolicyEntity policy = buildPolicy();
      when(policyResolver.resolveEffectivePolicies(eq(10L), eq(20L), any()))
          .thenReturn(Map.of(100L, policy, 200L, policy, 300L, policy));

      PricingSignalSet sig1 = signalSet(new BigDecimal("1000"), new BigDecimal("600"), 10);
      PricingSignalSet sig2 = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      PricingSignalSet sig3 = signalSet(new BigDecimal("1000"), null, 10);

      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, sig1, 200L, sig2, 300L, sig3));

      when(strategyRegistry.resolve(PolicyType.TARGET_MARGIN)).thenReturn(pricingStrategy);

      when(pricingStrategy.calculate(eq(sig1), any()))
          .thenReturn(StrategyResult.success(new BigDecimal("900"), "price cut"));
      when(pricingStrategy.calculate(eq(sig2), any()))
          .thenReturn(StrategyResult.success(new BigDecimal("1000"), "same price"));
      when(pricingStrategy.calculate(eq(sig3), any()))
          .thenReturn(StrategyResult.skip("COGS missing", "pricing.strategy.cogs_missing"));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenAnswer(i -> new PricingConstraintResolver.ConstraintResolution(
              i.getArgument(0), List.of()));

      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      when(explanationBuilder.buildChange(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn("CHANGE");
      when(explanationBuilder.buildSkip(any(), any(), any())).thenReturn("SKIP");
      when(explanationBuilder.buildHold(any(), any())).thenReturn("HOLD");
      lenient().when(objectMapper.readValue(eq("{}"), eq(PolicySnapshot.class)))
          .thenReturn(new PolicySnapshot(policy.getId(), policy.getVersion(),
              policy.getName(), policy.getStrategyType(), policy.getStrategyParams(),
              null, null, null, null, null, policy.getExecutionMode()));

      pricingRunService.executeRun(1L);

      verify(eventPublisher).publishEvent(eventCaptor.capture());
      PricingRunCompletedEvent event = eventCaptor.getValue();
      assertThat(event.changeCount()).isEqualTo(1);
      assertThat(event.skipCount()).isEqualTo(1);
      assertThat(event.holdCount()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("executeRun — decision entity fields")
  class DecisionEntityFields {

    @Test
    @DisplayName("computes priceChangeAmount and priceChangePct correctly")
    void should_computeChangeFields_when_pricesKnown() throws Exception {
      setupBasePipeline();

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      when(pricingStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(new BigDecimal("1200"), "increase"));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new PricingConstraintResolver.ConstraintResolution(
              new BigDecimal("1200"), List.of()));

      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      when(explanationBuilder.buildChange(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn("CHANGE");

      pricingRunService.executeRun(1L);

      verify(decisionRepository).saveAll(decisionsCaptor.capture());
      PriceDecisionEntity decision = decisionsCaptor.getValue().get(0);
      assertThat(decision.getPriceChangeAmount()).isEqualByComparingTo(new BigDecimal("200"));
      assertThat(decision.getPriceChangePct()).isEqualByComparingTo(new BigDecimal("20.0000"));
    }

    @Test
    @DisplayName("publishes PricingRunCompletedEvent with correct counters")
    void should_publishEvent_when_runCompletes() throws Exception {
      setupBasePipeline();

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      when(pricingStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(new BigDecimal("900"), "cut"));

      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new PricingConstraintResolver.ConstraintResolution(
              new BigDecimal("900"), List.of()));

      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      when(explanationBuilder.buildChange(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn("CHANGE");

      pricingRunService.executeRun(1L);

      verify(eventPublisher).publishEvent(eventCaptor.capture());
      PricingRunCompletedEvent event = eventCaptor.getValue();
      assertThat(event.pricingRunId()).isEqualTo(1L);
      assertThat(event.workspaceId()).isEqualTo(10L);
      assertThat(event.connectionId()).isEqualTo(20L);
      assertThat(event.changeCount()).isEqualTo(1);
      assertThat(event.finalStatus()).isEqualTo(RunStatus.COMPLETED);
    }
  }

  @Nested
  @DisplayName("executeRun — execution mode → action scheduling")
  class ExecutionModeScheduling {

    @Test
    @DisplayName("does not schedule action for RECOMMENDATION mode")
    void should_notScheduleAction_when_recommendationMode() throws Exception {
      PricePolicyEntity policy = buildPolicy();
      policy.setExecutionMode(ExecutionMode.RECOMMENDATION);
      setupPipelineWithPolicy(policy);

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      when(pricingStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(new BigDecimal("900"), "cut"));
      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new PricingConstraintResolver.ConstraintResolution(
              new BigDecimal("900"), List.of()));
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));
      when(explanationBuilder.buildChange(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn("CHANGE");
      lenient().when(objectMapper.readValue(any(String.class), eq(PolicySnapshot.class)))
          .thenReturn(new PolicySnapshot(1L, 1, "Test Policy", PolicyType.TARGET_MARGIN,
              "{}", null, null, null, null, null, ExecutionMode.RECOMMENDATION));

      pricingRunService.executeRun(1L);

      verify(actionScheduler, never()).scheduleAction(
          anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("schedules action for SEMI_AUTO mode")
    void should_scheduleAction_when_semiAutoMode() throws Exception {
      PricePolicyEntity policy = buildPolicy();
      policy.setExecutionMode(ExecutionMode.SEMI_AUTO);
      setupPipelineWithPolicy(policy);

      PricingSignalSet signals = signalSet(new BigDecimal("1000"), new BigDecimal("500"), 10);
      when(signalCollector.collectBatch(any(), eq(20L), anyInt()))
          .thenReturn(Map.of(100L, signals));

      when(pricingStrategy.calculate(any(), any()))
          .thenReturn(StrategyResult.success(new BigDecimal("900"), "cut"));
      when(constraintResolver.resolve(any(), any(), any()))
          .thenReturn(new PricingConstraintResolver.ConstraintResolution(
              new BigDecimal("900"), List.of()));
      when(guardChain.evaluate(any(), any(), any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));
      when(explanationBuilder.buildChange(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn("CHANGE");
      when(objectMapper.readValue(any(String.class), eq(PolicySnapshot.class)))
          .thenReturn(new PolicySnapshot(1L, 1, "Test Policy", PolicyType.TARGET_MARGIN,
              "{}", null, null, null, null, null, ExecutionMode.SEMI_AUTO));

      pricingRunService.executeRun(1L);

      verify(actionScheduler).scheduleAction(
          anyLong(), eq(100L), any(), eq(ExecutionMode.SEMI_AUTO), eq(10L));
    }
  }

  @Nested
  @DisplayName("executeRun — failure handling")
  class FailureHandling {

    @Test
    @DisplayName("sets FAILED status when exception occurs")
    void should_setFailed_when_exceptionOccurs() throws Exception {
      when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));
      when(runRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(automationBlockerChecker.isBlocked(10L, 20L))
          .thenThrow(new RuntimeException("DB error"));
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      pricingRunService.executeRun(1L);

      assertThat(runEntity.getStatus()).isEqualTo(RunStatus.FAILED);
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().finalStatus()).isEqualTo(RunStatus.FAILED);
    }
  }

  private void setupBasePipeline() throws Exception {
    PricePolicyEntity policy = buildPolicy();
    setupPipelineWithPolicy(policy);
  }

  private void setupPipelineWithPolicy(PricePolicyEntity policy) throws Exception {
    when(runRepository.findById(1L)).thenReturn(Optional.of(runEntity));
    when(runRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(automationBlockerChecker.isBlocked(10L, 20L)).thenReturn(false);
    lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    lenient().when(objectMapper.readValue(eq("{}"), eq(GuardConfig.class)))
        .thenReturn(GuardConfig.DEFAULTS);
    lenient().when(objectMapper.readValue(eq("{}"), eq(PolicySnapshot.class)))
        .thenReturn(new PolicySnapshot(policy.getId(), policy.getVersion(),
            policy.getName(), policy.getStrategyType(), policy.getStrategyParams(),
            null, null, null, null, null, policy.getExecutionMode()));
    lenient().when(decisionRepository.saveAll(any())).thenAnswer(i -> {
      List<PriceDecisionEntity> entities = i.getArgument(0);
      long idCounter = 100L;
      for (PriceDecisionEntity e : entities) {
        if (e.getId() == null) {
          e.setId(idCounter++);
        }
      }
      return entities;
    });

    OfferRow offer = new OfferRow(100L, 1L, 5L, 20L, "ACTIVE");
    when(dataReadRepository.findOffersByConnection(20L)).thenReturn(List.of(offer));

    when(policyResolver.resolveEffectivePolicies(eq(10L), eq(20L), any()))
        .thenReturn(Map.of(100L, policy));

    lenient().when(strategyRegistry.resolve(policy.getStrategyType())).thenReturn(pricingStrategy);
  }

  private PricePolicyEntity buildPolicy() {
    var policy = new PricePolicyEntity();
    policy.setId(1L);
    policy.setWorkspaceId(10L);
    policy.setName("Test Policy");
    policy.setStrategyType(PolicyType.TARGET_MARGIN);
    policy.setStrategyParams("{}");
    policy.setGuardConfig("{}");
    policy.setExecutionMode(ExecutionMode.RECOMMENDATION);
    policy.setVersion(1);
    policy.setPriority(0);
    return policy;
  }

  private PricingSignalSet signalSet(BigDecimal currentPrice, BigDecimal cogs, Integer stock) {
    return new PricingSignalSet(
        currentPrice, cogs, null, stock,
        false, false, null, null, null, null, null, null, null);
  }
}
