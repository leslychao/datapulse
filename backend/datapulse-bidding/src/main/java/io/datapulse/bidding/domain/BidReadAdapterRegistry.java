package io.datapulse.bidding.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BidReadAdapterRegistry {

  private final Map<String, BidReadAdapter> adapters;

  public BidReadAdapterRegistry(List<BidReadAdapter> adapterList) {
    this.adapters = adapterList.stream()
        .collect(Collectors.toMap(
            BidReadAdapter::marketplaceType, Function.identity()));
    log.info("BidReadAdapterRegistry initialized: marketplaces={}",
        adapters.keySet());
  }

  public BidReadAdapter resolve(String marketplaceType) {
    return adapters.get(marketplaceType);
  }

  public boolean supports(String marketplaceType) {
    return adapters.containsKey(marketplaceType);
  }
}
