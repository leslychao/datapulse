package io.datapulse.bidding.domain.guard;

import io.datapulse.bidding.domain.BiddingGuardContext;
import io.datapulse.bidding.domain.BiddingGuardResult;

public interface BiddingGuard {

  BiddingGuardResult evaluate(BiddingGuardContext context);

  String guardName();

  int order();
}
