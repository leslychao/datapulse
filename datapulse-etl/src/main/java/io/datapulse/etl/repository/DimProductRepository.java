package io.datapulse.etl.repository;

import io.datapulse.domain.MarketplaceType;
import java.util.List;

public interface DimProductRepository {

  void upsertOzon(Long accountId, String requestId);

  void upsertWildberries(Long accountId, String requestId);

  List<Long> fetchSourceProductIds(long accountId, MarketplaceType marketplaceType);

  void upsertOzonFromPostingsFbs(long accountId, String requestId);

  void upsertOzonFromPostingsFbo(long accountId, String requestId);

  void upsertWildberriesFromSales(long accountId, String requestId);
}
