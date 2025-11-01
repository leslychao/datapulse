package io.datapulse.etl.route;

import io.datapulse.core.mapper.wb.WbStockMapper;
import io.datapulse.domain.dto.StockDto;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.event.FetchRequest;
import io.datapulse.marketplaces.event.MarketplaceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public final class WbStocksEventSource implements EventSource<StockDto> {

  private final WbAdapter wbAdapter;
  private final WbStockMapper transformer;

  @Override
  public MarketplaceEvent event() {
    return MarketplaceEvent.STOCK_LEVEL;
  }

  @Override
  public Flux<StockDto> fetch(FetchRequest req) {
    return wbAdapter
        .fetchStock(req.accountId(), req.from())
        .map(transformer::toDto);
  }
}
