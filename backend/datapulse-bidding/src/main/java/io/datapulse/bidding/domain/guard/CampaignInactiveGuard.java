package io.datapulse.bidding.domain.guard;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;

/**
 * Blocks bidding when the advertising campaign is not active.
 * WB active status = "9"; other marketplaces will be added later.
 */
@Component
public class CampaignInactiveGuard implements BiddingGuard {

  private static final String WB_ACTIVE_STATUS = "9";

  @Override
  public String guardName() {
    return "campaign_inactive_guard";
  }

  @Override
  public int order() {
    return 20;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    String status = context.signals().campaignStatus();

    if (status == null || !WB_ACTIVE_STATUS.equals(status)) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_CAMPAIGN_INACTIVE);
    }
    return BiddingGuardResult.allow(guardName());
  }
}
