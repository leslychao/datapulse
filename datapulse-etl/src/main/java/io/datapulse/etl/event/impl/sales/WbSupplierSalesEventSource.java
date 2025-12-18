package io.datapulse.etl.event.impl.sales;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.sales.WbSupplierSaleRaw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@EtlSourceMeta(
    event = MarketplaceEvent.SALES_FACT,
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WB_SUPPLIER_SALES
)
public class WbSupplierSalesEventSource implements EventSource {

  private static final int FLAG_INCREMENTAL = 0;

  private final WbAdapter wbAdapter;

  public WbSupplierSalesEventSource(WbAdapter wbAdapter) {
    this.wbAdapter = wbAdapter;
  }

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    String cursor = dateFrom.toString();
    int noProgressRepeats = 0;

    while (true) {
      Snapshot<WbSupplierSaleRaw> snapshot =
          wbAdapter.downloadSupplierSalesPage(accountId, cursor, FLAG_INCREMENTAL);

      snapshots.add(snapshot);

      String next = snapshot.nextToken();
      if (next == null) {
        break;
      }

      if (next.equals(cursor)) {
        noProgressRepeats++;
        if (noProgressRepeats >= 2) {
          break;
        }
      } else {
        noProgressRepeats = 0;
      }

      cursor = next;
    }

    return snapshots;
  }
}
