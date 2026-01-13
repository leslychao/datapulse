package io.datapulse.etl.event.impl.inventory;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.inventory.WbStockRaw;
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
    events = {MarketplaceEvent.INVENTORY_FACT},
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WB_STOCKS
)
public class WbStocksEventSource implements EventSource {

  private final WbAdapter wbAdapter;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    String cursor = dateFrom.toString();

    while (true) {
      Snapshot<WbStockRaw> snapshot = wbAdapter.downloadStocksPage(accountId, cursor);

      snapshots.add(snapshot);

      String next = snapshot.nextToken();
      if (next == null) {
        break;
      }
      if (next.equals(cursor)) {
        break;
      }

      cursor = next;
    }

    return snapshots;
  }
}
