package io.datapulse.bidding.adapter;

import java.util.Map;

import io.datapulse.bidding.domain.BidActionGateway;
import io.datapulse.bidding.domain.BidActionGatewayResult;
import io.datapulse.bidding.persistence.BidActionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SimulatedBidActionGateway implements BidActionGateway {

  @Override
  public BidActionGatewayResult execute(BidActionEntity action,
      Map<String, String> credentials) {
    log.info("Simulated bid change: offerId={}, targetBid={}, "
            + "previousBid={}, marketplace={}",
        action.getMarketplaceOfferId(),
        action.getTargetBid(),
        action.getPreviousBid(),
        action.getMarketplaceType());

    return BidActionGatewayResult.success(action.getTargetBid(), null);
  }

  @Override
  public BidActionGatewayResult reconcile(BidActionEntity action,
      Map<String, String> credentials) {
    return BidActionGatewayResult.success(action.getTargetBid(), null);
  }

  @Override
  public String marketplaceType() {
    return "SIMULATED";
  }
}
