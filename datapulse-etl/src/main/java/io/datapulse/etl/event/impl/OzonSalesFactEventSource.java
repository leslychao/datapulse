package io.datapulse.etl.event.impl;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import java.time.LocalDate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EtlSourceMeta(
    event = MarketplaceEvent.SALES_FACT,
    marketplace = MarketplaceType.OZON
)
@Profile("!test")
public final class OzonSalesFactEventSource implements EventSource {

  private final OzonAdapter ozonAdapter;

  @Override
  public @NonNull Snapshot<OzonAnalyticsApiRaw> fetchSnapshot(
      long accountId,
      @NonNull MarketplaceEvent event,
      @NonNull LocalDate from,
      @NonNull LocalDate to
  ) {
    return ozonAdapter.downloadSalesSnapshot(accountId, from, to);
  }
}
