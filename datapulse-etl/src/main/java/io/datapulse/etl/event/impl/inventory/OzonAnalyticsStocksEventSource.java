package io.datapulse.etl.event.impl.inventory;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.inventory.OzonAnalyticsStocksRaw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    events = { MarketplaceEvent.INVENTORY_FACT },
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_ANALYTICS_STOCKS
)
public class OzonAnalyticsStocksEventSource implements EventSource {

  private static final int MAX_SKUS_PER_REQUEST = 100;

  private final OzonAdapter ozonAdapter;
  private final OzonAnalyticsSkuProvider skuProvider;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    List<Long> skus = skuProvider.resolveSkus(accountId);
    if (skus.isEmpty()) {
      return snapshots;
    }

    for (int fromIndex = 0; fromIndex < skus.size(); fromIndex += MAX_SKUS_PER_REQUEST) {
      int toIndex = Math.min(fromIndex + MAX_SKUS_PER_REQUEST, skus.size());
      List<Long> batch = skus.subList(fromIndex, toIndex);

      Snapshot<OzonAnalyticsStocksRaw> snapshot = ozonAdapter.downloadAnalyticsStocks(
          accountId,
          batch
      );

      snapshots.add(snapshot);
    }

    return snapshots;
  }
}
