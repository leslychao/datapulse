package io.datapulse.bidding.domain.guard;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;

/**
 * Blocks any bid change if the previous decision was made less than
 * {@code minDecisionIntervalHours} ago, preventing rapid bid oscillation.
 */
@Component
@RequiredArgsConstructor
public class FrequencyGuard implements BiddingGuard {

  private final BiddingProperties biddingProperties;

  @Override
  public String guardName() {
    return "frequency_guard";
  }

  @Override
  public int order() {
    return 55;
  }

  @Override
  public BiddingGuardResult evaluate(BiddingGuardContext context) {
    Integer hoursSinceLastChange = context.signals().hoursSinceLastChange();
    int minIntervalHours = biddingProperties.getMinDecisionIntervalHours();

    if (hoursSinceLastChange != null && hoursSinceLastChange < minIntervalHours) {
      return BiddingGuardResult.block(guardName(), MessageCodes.BIDDING_GUARD_FREQUENCY,
          Map.of("hours", minIntervalHours));
    }
    return BiddingGuardResult.allow(guardName());
  }
}
