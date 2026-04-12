package io.datapulse.bidding.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.bidding.persistence.ManualBidLockEntity;
import io.datapulse.bidding.persistence.ManualBidLockRepository;

@ExtendWith(MockitoExtension.class)
class ManualBidLockGuardTest {

  @Mock
  private ManualBidLockRepository manualBidLockRepository;

  @InjectMocks
  private ManualBidLockGuard guard;

  @Test
  @DisplayName("blocks when a manual bid lock exists for the offer")
  void should_block_when_lockExists() {
    when(manualBidLockRepository.findByWorkspaceIdAndMarketplaceOfferId(1L, 100L))
        .thenReturn(Optional.of(new ManualBidLockEntity()));

    BiddingGuardResult result = guard.evaluate(context(1L, 100L));

    assertThat(result.allowed()).isFalse();
    assertThat(result.guardName()).isEqualTo("manual_bid_lock_guard");
  }

  @Test
  @DisplayName("allows when no manual bid lock exists")
  void should_allow_when_noLock() {
    when(manualBidLockRepository.findByWorkspaceIdAndMarketplaceOfferId(1L, 100L))
        .thenReturn(Optional.empty());

    BiddingGuardResult result = guard.evaluate(context(1L, 100L));

    assertThat(result.allowed()).isTrue();
  }

  private BiddingGuardContext context(long workspaceId, long offerId) {
    return new BiddingGuardContext(
        offerId, workspaceId,
        TestSignals.defaults(),
        io.datapulse.bidding.domain.BidDecisionType.BID_UP,
        1000, 900, null);
  }
}
