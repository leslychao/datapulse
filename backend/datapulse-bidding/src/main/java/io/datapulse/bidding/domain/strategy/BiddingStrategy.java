package io.datapulse.bidding.domain.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import io.datapulse.bidding.domain.BiddingSignalSet;
import io.datapulse.bidding.domain.BiddingStrategyResult;
import io.datapulse.bidding.domain.BiddingStrategyType;

public interface BiddingStrategy {

  BiddingStrategyResult evaluate(BiddingSignalSet signals, JsonNode policyConfig);

  BiddingStrategyType strategyType();
}
