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

  private final OzonAdapter ozonAdapter;

  @Override
  public Snapshot<OzonProductInfoPricesItemRaw> fetchSnapshot(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    return ozonAdapter.downloadProductInfoPrices(accountId);
  }
}
