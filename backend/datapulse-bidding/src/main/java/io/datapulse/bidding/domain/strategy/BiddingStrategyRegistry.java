package io.datapulse.bidding.domain.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.datapulse.bidding.domain.BiddingStrategyType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BiddingStrategyRegistry {

  private final Map<BiddingStrategyType, BiddingStrategy> strategies;

  public BiddingStrategyRegistry(List<BiddingStrategy> strategyList) {
    this.strategies = new EnumMap<>(BiddingStrategyType.class);
    for (BiddingStrategy strategy : strategyList) {
      BiddingStrategy existing = strategies.put(strategy.strategyType(), strategy);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate BiddingStrategy for type %s: %s vs %s"
                .formatted(strategy.strategyType(),
                    existing.getClass().getSimpleName(),
                    strategy.getClass().getSimpleName()));
      }
      log.info("Registered bidding strategy: type={}, class={}",
          strategy.strategyType(), strategy.getClass().getSimpleName());
    }
  }

  public BiddingStrategy resolve(BiddingStrategyType type) {
    BiddingStrategy strategy = strategies.get(type);
    if (strategy == null) {
      throw new IllegalArgumentException(
          "No BiddingStrategy registered for type: " + type);
    }
    return strategy;
  }
}
