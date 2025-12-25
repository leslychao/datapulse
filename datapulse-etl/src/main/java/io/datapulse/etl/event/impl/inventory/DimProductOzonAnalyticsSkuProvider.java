package io.datapulse.etl.event.impl.inventory;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.repository.DimProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class DimProductOzonAnalyticsSkuProvider implements OzonAnalyticsSkuProvider {

  private final DimProductRepository repository;

  @Override
  public List<Long> resolveSkus(long accountId) {
    return repository.fetchSourceProductIds(accountId, MarketplaceType.OZON);
  }
}
