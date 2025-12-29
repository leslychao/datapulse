package io.datapulse.etl.event.impl.sales;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.sales.OzonPostingFboRaw;
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
    events = { MarketplaceEvent.SALES_FACT },
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_POSTINGS_FBO
)
public class OzonPostingsFboEventSource implements EventSource {

  private static final int PAGE_LIMIT = 1000;

  private final OzonAdapter ozonAdapter;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    long offset = 0L;

    while (true) {
      Snapshot<OzonPostingFboRaw> snapshot =
          ozonAdapter.downloadPostingsFboPage(accountId, dateFrom, dateTo, offset, PAGE_LIMIT, "");

      snapshots.add(snapshot);

      String next = snapshot.nextToken();
      if (next == null) {
        break;
      }
      if (next.equals(String.valueOf(offset))) {
        break;
      }

      offset = Long.parseLong(next);
    }

    return snapshots;
  }
}
