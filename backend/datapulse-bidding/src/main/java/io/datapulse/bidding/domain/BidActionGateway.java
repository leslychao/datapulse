package io.datapulse.bidding.domain;

import java.util.Map;

import io.datapulse.bidding.persistence.BidActionEntity;

public interface BidActionGateway {

  BidActionGatewayResult execute(BidActionEntity action, Map<String, String> credentials);

  BidActionGatewayResult reconcile(BidActionEntity action, Map<String, String> credentials);

  String marketplaceType();
}
