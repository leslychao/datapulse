package io.datapulse.etl.event.impl.product;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.product.OzonProductListItemRaw;
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
    event = MarketplaceEvent.PRODUCT_DICT,
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_PRODUCTS
)
public class OzonProductsEventSource implements EventSource {

  private static final int PAGE_SIZE = 1000;

  private final OzonAdapter ozonAdapter;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    String lastId = "";

    while (true) {
      Snapshot<OzonProductListItemRaw> snapshot =
          ozonAdapter.downloadProductsPage(accountId, lastId, PAGE_SIZE);

      snapshots.add(snapshot);

      String nextLastId = snapshot.nextToken();
      if (nextLastId == null || nextLastId.isBlank() || nextLastId.equals(lastId)) {
        break;
      }

      lastId = nextLastId;
    }

    return snapshots;
  }
}
