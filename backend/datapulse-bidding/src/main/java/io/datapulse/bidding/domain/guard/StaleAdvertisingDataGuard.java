package io.datapulse.bidding.domain.guard;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;

/**
 * Blocks bidding when advertising data appears stale:
 * either no activity at all (impressions, clicks, ad orders all zero)
 * or the last bid change was too long ago.
 */
@Component
@RequiredArgsConstructor
public class StaleAdvertisingDataGuard implements BiddingGuard {

  private final BiddingProperties biddingProperties;

  @Override
  public String guardName() {
    return "stale_advertising_data_guard";
  }

  @Override
  public int order() {
    return 30;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    BiddingSignalSet signals = context.signals();
    int thresholdHours = biddingProperties.getStaleDataThresholdHours();
    int thresholdDays = thresholdHours / 24;

    if (signals.impressions() == 0 && signals.clicks() == 0 && signals.adOrders() == 0) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_STALE_DATA,
          Map.of("hours", thresholdHours));
    }

    if (signals.daysSinceLastChange() != null && signals.daysSinceLastChange() > thresholdDays) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_STALE_DATA,
          Map.of("hours", thresholdHours));
    }

    return BiddingGuardResult.allow(guardName());
  }
}
