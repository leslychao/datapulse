package io.datapulse.marketplaces.event;

import static io.datapulse.domain.MarketplaceType.WILDBERRIES;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class WbSalesEventFetcher implements EventFetcher<WbSaleRaw> {

  private final WbAdapter wbAdapter;

  @Override
  public BusinessEvent event() {
    return BusinessEvent.SALES_FACT;
  }

  @Override
  public MarketplaceType marketplace() {
    return WILDBERRIES;
  }

  @Override
  public Flux<WbSaleRaw> fetch(FetchRequest req) {
    return wbAdapter.fetchSales(req.accountId(), req.from(), req.to());
  }
}
