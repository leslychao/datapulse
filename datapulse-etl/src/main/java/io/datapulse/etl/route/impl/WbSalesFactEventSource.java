package io.datapulse.etl.route.impl;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.wb.WbRealizationRaw;
import io.datapulse.domain.marketplace.Snapshot;
import io.datapulse.etl.route.EtlSourceMeta;
import io.datapulse.etl.route.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import java.time.LocalDate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.SALES_FACT,
    marketplace = MarketplaceType.WILDBERRIES
)
public final class WbSalesFactEventSource implements EventSource {

  private final WbAdapter wbAdapter;

  @Override
  public @NonNull Snapshot<WbRealizationRaw> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  ) {
    return wbAdapter.downloadSalesSnapshot(accountId, from, to);
  }
}
