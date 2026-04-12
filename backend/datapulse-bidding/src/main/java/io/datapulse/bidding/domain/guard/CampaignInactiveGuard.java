package io.datapulse.bidding.domain.guard;

import java.util.Set;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;

/**
 * Blocks bidding when the advertising campaign is not active.
 * <p>
 * Marketplace-specific statuses:
 * - WB: "9" = active
 * - Ozon: "ACTIVE" = active
 * - Yandex: no campaign concept for Sales Boost, always ALLOW.
 *   Yandex bids are per-SKU, not per-campaign. If campaignStatus is null
 *   and the offer has a YANDEX connection, this guard passes.
 */
@Component
public class CampaignInactiveGuard implements BiddingGuard {

  private static final Set<String> ACTIVE_STATUSES =
      Set.of("9", "ACTIVE", "active");

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

    if (status == null) {
      return BiddingGuardResult.allow(guardName());
    }

    if (ACTIVE_STATUSES.contains(status)) {
      return BiddingGuardResult.allow(guardName());
    }

    return BiddingGuardResult.block(
        guardName(), MessageCodes.BIDDING_GUARD_CAMPAIGN_INACTIVE);
  }
}
