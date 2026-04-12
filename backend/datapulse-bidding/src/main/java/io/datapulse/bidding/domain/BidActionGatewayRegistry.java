package io.datapulse.bidding.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class BidActionGatewayRegistry {

  private static final String SIMULATED = "SIMULATED";

  private final Map<String, BidActionGateway> gateways;

  public BidActionGatewayRegistry(List<BidActionGateway> gatewayList) {
    this.gateways = gatewayList.stream()
        .collect(Collectors.toMap(
            BidActionGateway::marketplaceType, Function.identity()));
  }

  /**
   * Resolves the appropriate gateway. For RECOMMENDATION execution mode,
   * always returns the simulated gateway regardless of marketplace type.
   */
  public BidActionGateway resolve(String marketplaceType,
      ExecutionMode executionMode) {
    if (executionMode == ExecutionMode.RECOMMENDATION) {
      BidActionGateway simulated = gateways.get(SIMULATED);
      if (simulated != null) {
        return simulated;
      }
    }
    return resolve(marketplaceType);
  }

  public BidActionGateway resolve(String marketplaceType) {
    BidActionGateway gateway = gateways.get(marketplaceType);
    if (gateway == null) {
      throw new IllegalStateException(
          "No BidActionGateway for marketplace: " + marketplaceType);
    }
    return gateway;
  }

  public boolean supports(String marketplaceType) {
    return gateways.containsKey(marketplaceType);
  }
}
