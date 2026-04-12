package io.datapulse.bidding.domain.guard;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.bidding.persistence.ManualBidLockRepository;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ManualBidLockGuard implements BiddingGuard {

  private final ManualBidLockRepository manualBidLockRepository;

  @Override
  public String guardName() {
    return "manual_bid_lock_guard";
  }

  @Override
  public int order() {
    return 10;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    boolean hasLock = manualBidLockRepository
        .findByWorkspaceIdAndMarketplaceOfferId(
            context.workspaceId(), context.marketplaceOfferId())
        .isPresent();

    if (hasLock) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_MANUAL_LOCK);
    }
    return BiddingGuardResult.allow(guardName());
  }
}
