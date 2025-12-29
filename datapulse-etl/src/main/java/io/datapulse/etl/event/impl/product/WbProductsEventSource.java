package io.datapulse.etl.event.impl.product;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.product.WbProductCardRaw;
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
    events = { MarketplaceEvent.PRODUCT_DICT },
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WB_PRODUCTS
)
public class WbProductsEventSource implements EventSource {

  private static final int PAGE_SIZE = 100;

  private final WbAdapter wbAdapter;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    String cursor = "";

    while (true) {
      Snapshot<WbProductCardRaw> snapshot =
          wbAdapter.downloadProductCards(accountId, cursor, PAGE_SIZE);

      snapshots.add(snapshot);

      String nextCursor = snapshot.nextToken();
      if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) {
        break;
      }

      cursor = nextCursor;
    }

    return snapshots;
  }
}
