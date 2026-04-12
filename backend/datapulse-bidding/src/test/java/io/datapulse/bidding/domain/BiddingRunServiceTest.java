package io.datapulse.bidding.domain;

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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.event.BiddingRunCompletedEvent;
import io.datapulse.bidding.domain.guard.BiddingGuardChain;
import io.datapulse.bidding.domain.guard.BiddingGuardChain.GuardChainResult;
import io.datapulse.bidding.domain.strategy.BiddingStrategy;
import io.datapulse.bidding.domain.strategy.BiddingStrategyRegistry;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
import io.datapulse.bidding.persistence.BiddingDataReadRepository;
import io.datapulse.bidding.persistence.BiddingRunEntity;
import io.datapulse.bidding.persistence.BiddingRunRepository;
import io.datapulse.bidding.persistence.EligibleProductRow;

@ExtendWith(MockitoExtension.class)
class BiddingRunServiceTest {

  @Mock private BiddingStrategyRegistry strategyRegistry;
  @Mock private BiddingGuardChain guardChain;
  @Mock private BiddingSignalCollector signalCollector;
  @Mock private BiddingDataReadRepository readRepo;
  @Mock private BidDecisionRepository decisionRepository;
  @Mock private BiddingRunRepository runRepository;
  @Mock private BidPolicyRepository policyRepository;
  @Mock private BiddingActionScheduler actionScheduler;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private BiddingStrategy mockStrategy;

  @Captor private ArgumentCaptor<BidDecisionEntity> decisionCaptor;
  @Captor private ArgumentCaptor<BiddingRunEntity> runCaptor;
  @Captor private ArgumentCaptor<BiddingRunCompletedEvent> eventCaptor;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BiddingProperties properties =
      new BiddingProperties(7, 50, 4, 48, 7, true);

  private BiddingRunService service;
  private BidPolicyEntity activePolicy;

  @BeforeEach
  void setUp() {
    service = new BiddingRunService(
        strategyRegistry, guardChain, signalCollector, readRepo,
        decisionRepository, runRepository, policyRepository,
        actionScheduler, properties, objectMapper, eventPublisher);

    activePolicy = new BidPolicyEntity();
    activePolicy.setId(10L);
    activePolicy.setWorkspaceId(1L);
    activePolicy.setName("Test Economy");
    activePolicy.setStrategyType(BiddingStrategyType.ECONOMY_HOLD);
    activePolicy.setExecutionMode(ExecutionMode.FULL_AUTO);
    activePolicy.setStatus(BidPolicyStatus.ACTIVE);
    activePolicy.setConfig("{\"targetDrrPct\": 10}");

    lenient().when(runRepository.save(any(BiddingRunEntity.class)))
        .thenAnswer(inv -> {
          BiddingRunEntity run = inv.getArgument(0);
          if (run.getId() == null) run.setId(1L);
          return run;
        });
  }

  @Nested
  @DisplayName("Full pipeline")
  class FullPipeline {

    @Test
    @DisplayName("creates decisions when run is executed for eligible products")
    void should_createDecisions_when_runExecuted() {
      setupPipeline();

      BiddingSignalSet signals = defaultSignals();
      when(signalCollector.collect(eq(1L), eq(100L), eq("SKU1"), anyInt()))
          .thenReturn(signals);
      when(signalCollector.hasMinimumData(signals)).thenReturn(true);

      when(mockStrategy.evaluate(any(), any()))
          .thenReturn(new BiddingStrategyResult(BidDecisionType.BID_DOWN, 800, "bid down"));
      when(guardChain.evaluate(any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      service.executeRun(1L, 10L);

      verify(decisionRepository).save(decisionCaptor.capture());
      BidDecisionEntity decision = decisionCaptor.getValue();
      assertThat(decision.getDecisionType()).isEqualTo(BidDecisionType.BID_DOWN);
      assertThat(decision.getTargetBid()).isEqualTo(800);
      assertThat(decision.getStrategyType()).isEqualTo(BiddingStrategyType.ECONOMY_HOLD);

      verify(actionScheduler).scheduleActions(1L);
    }

    @Test
    @DisplayName("creates HOLD decision when guard blocks the proposed change")
    void should_holdDecision_when_guardBlocks() {
      setupPipeline();

      BiddingSignalSet signals = defaultSignals();
      when(signalCollector.collect(eq(1L), eq(100L), eq("SKU1"), anyInt()))
          .thenReturn(signals);
      when(signalCollector.hasMinimumData(signals)).thenReturn(true);

      when(mockStrategy.evaluate(any(), any()))
          .thenReturn(new BiddingStrategyResult(BidDecisionType.BID_UP, 1100, "bid up"));

      BiddingGuardResult blocking = BiddingGuardResult.block(
          "stale_advertising_data_guard", "bidding.guard.stale_data.blocked");
      when(guardChain.evaluate(any()))
          .thenReturn(new GuardChainResult(false, blocking, List.of()));

      service.executeRun(1L, 10L);

      verify(decisionRepository).save(decisionCaptor.capture());
      BidDecisionEntity decision = decisionCaptor.getValue();
      assertThat(decision.getDecisionType()).isEqualTo(BidDecisionType.HOLD);
      assertThat(decision.getExplanationSummary()).contains("stale_advertising_data_guard");
    }
  }

  @Nested
  @DisplayName("Blast radius")
  class BlastRadius {

    @Test
    @DisplayName("pauses run when blast radius is exceeded (>50% BID_UP)")
    void should_pauseRun_when_blastRadiusExceeded() {
      when(policyRepository.findById(10L)).thenReturn(Optional.of(activePolicy));
      when(strategyRegistry.resolve(BiddingStrategyType.ECONOMY_HOLD))
          .thenReturn(mockStrategy);

      List<EligibleProductRow> products = List.of(
          new EligibleProductRow(101L, "SKU1", 5L),
          new EligibleProductRow(102L, "SKU2", 5L),
          new EligibleProductRow(103L, "SKU3", 5L));
      when(readRepo.findEligibleProducts(1L, 10L)).thenReturn(products);

      BiddingSignalSet signals = defaultSignals();
      when(signalCollector.collect(eq(1L), anyLong(), any(), anyInt()))
          .thenReturn(signals);
      when(signalCollector.hasMinimumData(any())).thenReturn(true);

      when(mockStrategy.evaluate(any(), any()))
          .thenReturn(new BiddingStrategyResult(BidDecisionType.BID_UP, 1100, "bid up"));
      when(guardChain.evaluate(any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      service.executeRun(1L, 10L);

      verify(runRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
      BiddingRunEntity finalRun = runCaptor.getAllValues()
          .stream().filter(r -> r.getCompletedAt() != null).findFirst().orElse(null);

      assertThat(finalRun).isNotNull();
      assertThat(finalRun.getStatus()).isEqualTo(BiddingRunStatus.PAUSED);
      assertThat(finalRun.getErrorMessage()).contains("Blast radius");

      verify(actionScheduler, never()).scheduleActions(anyLong());
    }
  }

  @Nested
  @DisplayName("Skipping scenarios")
  class Skipping {

    @Test
    @DisplayName("skips product with insufficient data (HOLD)")
    void should_holdDecision_when_insufficientData() {
      setupPipeline();

      BiddingSignalSet signals = defaultSignals();
      when(signalCollector.collect(eq(1L), eq(100L), eq("SKU1"), anyInt()))
          .thenReturn(signals);
      when(signalCollector.hasMinimumData(signals)).thenReturn(false);

      service.executeRun(1L, 10L);

      verify(decisionRepository).save(decisionCaptor.capture());
      BidDecisionEntity decision = decisionCaptor.getValue();
      assertThat(decision.getDecisionType()).isEqualTo(BidDecisionType.HOLD);
    }

    @Test
    @DisplayName("skips when policy is not ACTIVE")
    void should_skipRun_when_policyNotActive() {
      activePolicy.setStatus(BidPolicyStatus.PAUSED);
      when(policyRepository.findById(10L)).thenReturn(Optional.of(activePolicy));

      service.executeRun(1L, 10L);

      verify(readRepo, never()).findEligibleProducts(anyLong(), anyLong());
      verify(decisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("skips when policy not found")
    void should_skipRun_when_policyNotFound() {
      when(policyRepository.findById(10L)).thenReturn(Optional.empty());

      service.executeRun(1L, 10L);

      verify(readRepo, never()).findEligibleProducts(anyLong(), anyLong());
    }
  }

  @Nested
  @DisplayName("Event publishing")
  class Events {

    @Test
    @DisplayName("publishes BiddingRunCompletedEvent with correct counters")
    void should_publishEvent_when_runCompletes() {
      setupPipeline();

      BiddingSignalSet signals = defaultSignals();
      when(signalCollector.collect(eq(1L), eq(100L), eq("SKU1"), anyInt()))
          .thenReturn(signals);
      when(signalCollector.hasMinimumData(signals)).thenReturn(true);

      when(mockStrategy.evaluate(any(), any()))
          .thenReturn(new BiddingStrategyResult(BidDecisionType.BID_DOWN, 800, "bid down"));
      when(guardChain.evaluate(any()))
          .thenReturn(new GuardChainResult(true, null, List.of()));

      service.executeRun(1L, 10L);

      verify(eventPublisher).publishEvent(eventCaptor.capture());
      BiddingRunCompletedEvent event = eventCaptor.getValue();
      assertThat(event.workspaceId()).isEqualTo(1L);
      assertThat(event.bidPolicyId()).isEqualTo(10L);
      assertThat(event.totalBidDown()).isEqualTo(1);
    }
  }

  private void setupPipeline() {
    when(policyRepository.findById(10L)).thenReturn(Optional.of(activePolicy));
    when(strategyRegistry.resolve(BiddingStrategyType.ECONOMY_HOLD))
        .thenReturn(mockStrategy);
    when(readRepo.findEligibleProducts(1L, 10L))
        .thenReturn(List.of(new EligibleProductRow(100L, "SKU1", 5L)));
  }

  private BiddingSignalSet defaultSignals() {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        100, 10, 5, BigDecimal.TEN,
        new BigDecimal("20.0"), 30, null, null, 50,
        null, null, "9");
  }
}
