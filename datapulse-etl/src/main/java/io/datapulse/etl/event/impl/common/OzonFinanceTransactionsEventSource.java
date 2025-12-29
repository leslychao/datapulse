package io.datapulse.etl.event.impl.common;

import static io.datapulse.etl.MarketplaceEvent.FACT_LOGISTICS_COSTS;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.sales.OzonFinanceTransactionOperationRaw;
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
    events = { MarketplaceEvent.SALES_FACT, FACT_LOGISTICS_COSTS },
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS
)
public class OzonFinanceTransactionsEventSource implements EventSource {

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

    long page = 1L;

    while (true) {
      Snapshot<OzonFinanceTransactionOperationRaw> snapshot =
          ozonAdapter.downloadFinanceTransactionsPage(accountId, dateFrom, dateTo, page, PAGE_SIZE);

      snapshots.add(snapshot);

      String next = snapshot.nextToken();
      if (next == null) {
        break;
      }
      if (next.equals(String.valueOf(page))) {
        break;
      }

      page = Long.parseLong(next);
    }

    return snapshots;
  }
}
