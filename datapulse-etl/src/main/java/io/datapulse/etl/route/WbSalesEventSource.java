package io.datapulse.etl.route;

import io.datapulse.domain.dto.SaleDto;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.event.MarketplaceEvent;
import io.datapulse.marketplaces.event.FetchRequest;
import io.datapulse.marketplaces.event.transform.WbSalesTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public final class WbSalesEventSource implements EventSource<SaleDto> {

  private final WbAdapter wbAdapter;
  private final WbSalesTransformer transformer;

  @Override
  public MarketplaceEvent event() {
    return MarketplaceEvent.SALES_FACT;
  }

  @Override
  public Flux<SaleDto> fetch(FetchRequest req) {
    return wbAdapter
        .fetchSales(req.accountId(), req.from(), req.to())
        .map(transformer::transform);
  }
}
