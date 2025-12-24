package io.datapulse.etl.event.impl.inventory;

import io.datapulse.etl.repository.jdbc.RawOzonProductInfoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class DimProductOzonAnalyticsSkuProvider implements OzonAnalyticsSkuProvider {

  private final RawOzonProductInfoRepository repository;

  @Override
  public List<String> resolveSkus(long accountId) {
    List<String> skus = repository.fetchAllSkus(accountId);

    return skus.stream()
        .distinct()
        .sorted()
        .toList();
  }
}
