package io.datapulse.etl.event.impl.tariff;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.tariff.OzonProductInfoPricesItemRaw;
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
    event = MarketplaceEvent.COMMISSION_DICT,
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_PRODUCT_INFO_PRICES
)
public class OzonProductInfoPricesEventSource implements EventSource {

  private static final int PAGE_SIZE = 100;

  private final OzonAdapter ozonAdapter;

  @Override
  public Snapshot<OzonProductInfoPricesItemRaw> fetchSnapshot(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    return ozonAdapter.downloadProductInfoPricesPage(accountId, "", PAGE_SIZE);
  }

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
      Snapshot<OzonProductInfoPricesItemRaw> snapshot =
          ozonAdapter.downloadProductInfoPricesPage(accountId, cursor, PAGE_SIZE);

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
