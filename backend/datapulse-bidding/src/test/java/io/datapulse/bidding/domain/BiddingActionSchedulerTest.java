package io.datapulse.bidding.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.bidding.persistence.BidActionRepository;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import io.datapulse.bidding.persistence.BiddingDataReadRepository;
import io.datapulse.bidding.persistence.CampaignInfoRow;
import io.datapulse.bidding.persistence.EligibleProductRow;
import io.datapulse.platform.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
class BiddingActionSchedulerTest {

  @Mock private BidDecisionRepository decisionRepository;
  @Mock private BidActionRepository actionRepository;
  @Mock private BiddingDataReadRepository readRepository;
  @Mock private OutboxService outboxService;

  @Captor private ArgumentCaptor<BidActionEntity> actionCaptor;
  @Captor private ArgumentCaptor<List<BidActionEntity>> actionsCaptor;

  private BiddingActionScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new BiddingActionScheduler(
        decisionRepository, actionRepository, readRepository, outboxService);
  }

  @Nested
  @DisplayName("PAUSE decisions")
  class PauseDecisions {

    @Test
    @DisplayName("creates action with targetBid=0 for PAUSE decision")
    void should_createAction_when_decisionType_isPAUSE() {
      BidDecisionEntity decision = buildDecision(
          1L, BidDecisionType.PAUSE, null, 1000, "FULL_AUTO");
      setupStubs(decision);

      scheduler.scheduleActions(100L);

      verify(actionRepository).save(actionCaptor.capture());
      BidActionEntity action = actionCaptor.getValue();
      assertThat(action.getTargetBid()).isEqualTo(0);
      assertThat(action.getPreviousBid()).isEqualTo(1000);
      assertThat(action.getMarketplaceOfferId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("PAUSE supersedes existing pre-exec actions")
    void should_supersede_existingActions_when_newPAUSE() {
      BidDecisionEntity decision = buildDecision(
          1L, BidDecisionType.PAUSE, null, 1000, "FULL_AUTO");
      setupStubs(decision);

      BidActionEntity oldAction = new BidActionEntity();
      oldAction.setId(50L);
      oldAction.setStatus(BidActionStatus.PENDING_APPROVAL);
      when(actionRepository.findByMarketplaceOfferIdAndStatusIn(1L, List.of(
          BidActionStatus.PENDING_APPROVAL, BidActionStatus.APPROVED,
          BidActionStatus.SCHEDULED, BidActionStatus.ON_HOLD)))
          .thenReturn(List.of(oldAction));

      scheduler.scheduleActions(100L);

      assertThat(oldAction.getStatus()).isEqualTo(BidActionStatus.SUPERSEDED);
      verify(actionRepository).saveAll(List.of(oldAction));
      verify(actionRepository).save(actionCaptor.capture());
      assertThat(actionCaptor.getValue().getTargetBid()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("RESUME decisions")
  class ResumeDecisions {

    @Test
    @DisplayName("creates action with targetBid from decision for RESUME")
    void should_createAction_when_decisionType_isRESUME() {
      BidDecisionEntity decision = buildDecision(
          1L, BidDecisionType.RESUME, 900, 1000, "FULL_AUTO");
      setupStubs(decision);

      scheduler.scheduleActions(100L);

      verify(actionRepository).save(actionCaptor.capture());
      BidActionEntity action = actionCaptor.getValue();
      assertThat(action.getTargetBid()).isEqualTo(900);
      assertThat(action.getPreviousBid()).isEqualTo(1000);
    }
  }

  @Nested
  @DisplayName("HOLD decisions (non-actionable)")
  class HoldDecisions {

    @Test
    @DisplayName("does not create action for HOLD decision")
    void should_skipNonActionableDecisions() {
      BidDecisionEntity decision = buildDecision(
          1L, BidDecisionType.HOLD, null, 1000, "FULL_AUTO");
      when(decisionRepository.findByBiddingRunId(100L))
          .thenReturn(List.of(decision));

      scheduler.scheduleActions(100L);

      verify(actionRepository, never()).save(any(BidActionEntity.class));
    }
  }

  @Nested
  @DisplayName("Execution mode → initial status")
  class ExecutionModeStatus {

    @Test
    @DisplayName("FULL_AUTO → APPROVED status")
    void should_setStatusApproved_when_fullAuto() {
      BidDecisionEntity decision = buildDecision(
          1L, BidDecisionType.BID_UP, 1100, 1000, "FULL_AUTO");
      setupStubs(decision);

      scheduler.scheduleActions(100L);

      verify(actionRepository).save(actionCaptor.capture());
      assertThat(actionCaptor.getValue().getStatus())
          .isEqualTo(BidActionStatus.APPROVED);
      assertThat(actionCaptor.getValue().getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("SEMI_AUTO → PENDING_APPROVAL status")
    void should_setStatusPendingApproval_when_semiAuto() {
      BidDecisionEntity decision = buildDecision(
          1L, BidDecisionType.BID_DOWN, 800, 1000, "SEMI_AUTO");
      setupStubs(decision);

      scheduler.scheduleActions(100L);

      verify(actionRepository).save(actionCaptor.capture());
      assertThat(actionCaptor.getValue().getStatus())
          .isEqualTo(BidActionStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("RECOMMENDATION → ON_HOLD status")
    void should_setStatusOnHold_when_recommendation() {
      BidDecisionEntity decision = buildDecision(
          1L, BidDecisionType.BID_UP, 1100, 1000, "RECOMMENDATION");
      setupStubs(decision);

      scheduler.scheduleActions(100L);

      verify(actionRepository).save(actionCaptor.capture());
      assertThat(actionCaptor.getValue().getStatus())
          .isEqualTo(BidActionStatus.ON_HOLD);
    }
  }

  @Nested
  @DisplayName("All actionable types")
  class ActionableTypes {

    @Test
    @DisplayName("creates actions for all actionable decision types")
    void should_createActions_forAllActionableDecisions() {
      List<BidDecisionEntity> decisions = List.of(
          buildDecision(1L, BidDecisionType.BID_UP, 1100, 1000, "FULL_AUTO"),
          buildDecision(2L, BidDecisionType.BID_DOWN, 800, 1000, "FULL_AUTO"),
          buildDecision(3L, BidDecisionType.PAUSE, null, 1000, "FULL_AUTO"),
          buildDecision(4L, BidDecisionType.RESUME, 900, 1000, "FULL_AUTO"),
          buildDecision(5L, BidDecisionType.SET_MINIMUM, 50, 1000, "FULL_AUTO"),
          buildDecision(6L, BidDecisionType.EMERGENCY_CUT, 200, 1000, "FULL_AUTO"));

      when(decisionRepository.findByBiddingRunId(100L)).thenReturn(decisions);
      when(actionRepository.save(any(BidActionEntity.class))).thenAnswer(inv -> {
        BidActionEntity e = inv.getArgument(0);
        if (e.getId() == null) e.setId(1L);
        return e;
      });

      for (BidDecisionEntity d : decisions) {
        when(actionRepository.findByMarketplaceOfferIdAndStatusIn(
            d.getMarketplaceOfferId(), List.of(
                BidActionStatus.PENDING_APPROVAL, BidActionStatus.APPROVED,
                BidActionStatus.SCHEDULED, BidActionStatus.ON_HOLD)))
            .thenReturn(List.of());
        when(readRepository.findCampaignInfo(d.getMarketplaceOfferId()))
            .thenReturn(Optional.of(
                new CampaignInfoRow("camp-" + d.getMarketplaceOfferId(), "9", "WB")));
        when(readRepository.findEligibleProducts(1L, 10L))
            .thenReturn(List.of(
                new EligibleProductRow(1L, "SKU1", 5L),
                new EligibleProductRow(2L, "SKU2", 5L),
                new EligibleProductRow(3L, "SKU3", 5L),
                new EligibleProductRow(4L, "SKU4", 5L),
                new EligibleProductRow(5L, "SKU5", 5L),
                new EligibleProductRow(6L, "SKU6", 5L)));
      }

      scheduler.scheduleActions(100L);

      verify(actionRepository, org.mockito.Mockito.times(6))
          .save(actionCaptor.capture());
      assertThat(actionCaptor.getAllValues()).hasSize(6);
    }
  }

  private BidDecisionEntity buildDecision(
      long offerId, BidDecisionType type,
      Integer targetBid, Integer currentBid,
      String executionMode) {
    var d = new BidDecisionEntity();
    d.setId(offerId * 10);
    d.setBiddingRunId(100L);
    d.setWorkspaceId(1L);
    d.setMarketplaceOfferId(offerId);
    d.setBidPolicyId(10L);
    d.setStrategyType(BiddingStrategyType.ECONOMY_HOLD);
    d.setDecisionType(type);
    d.setTargetBid(targetBid);
    d.setCurrentBid(currentBid);
    d.setExecutionMode(executionMode);
    return d;
  }

  private void setupStubs(BidDecisionEntity decision) {
    when(decisionRepository.findByBiddingRunId(100L))
        .thenReturn(List.of(decision));
    when(actionRepository.findByMarketplaceOfferIdAndStatusIn(
        decision.getMarketplaceOfferId(), List.of(
            BidActionStatus.PENDING_APPROVAL, BidActionStatus.APPROVED,
            BidActionStatus.SCHEDULED, BidActionStatus.ON_HOLD)))
        .thenReturn(List.of());
    when(actionRepository.save(any(BidActionEntity.class))).thenAnswer(inv -> {
      BidActionEntity e = inv.getArgument(0);
      if (e.getId() == null) e.setId(1L);
      return e;
    });
    when(readRepository.findCampaignInfo(decision.getMarketplaceOfferId()))
        .thenReturn(Optional.of(new CampaignInfoRow("camp-123", "9", "WB")));
    when(readRepository.findEligibleProducts(1L, 10L))
        .thenReturn(List.of(new EligibleProductRow(
            decision.getMarketplaceOfferId(), "SKU1", 5L)));
  }
}
