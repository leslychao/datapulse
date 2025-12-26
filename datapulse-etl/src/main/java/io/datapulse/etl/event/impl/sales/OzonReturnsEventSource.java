package io.datapulse.etl.event.impl.sales;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.sales.OzonReturnRaw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@EtlSourceMeta(
    event = MarketplaceEvent.SALES_FACT,
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_RETURNS
)
public class OzonReturnsEventSource implements EventSource {

  private static final int PAGE_SIZE = 500;

  private final OzonAdapter ozonAdapter;

  public OzonReturnsEventSource(OzonAdapter ozonAdapter) {
    this.ozonAdapter = ozonAdapter;
  }

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    long lastId = 0L;
    String cursor = String.valueOf(lastId);
    int noProgressRepeats = 0;

    while (true) {
      Snapshot<OzonReturnRaw> snapshot =
          ozonAdapter.downloadReturnsPage(accountId, dateFrom, dateTo, lastId, PAGE_SIZE);

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
      lastId = Long.parseLong(next);
    }

    return snapshots;
  }
}
