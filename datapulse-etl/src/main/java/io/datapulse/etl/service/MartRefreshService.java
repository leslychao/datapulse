package io.datapulse.etl.service;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.repository.OrderPnlMartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MartRefreshService {

  private final OrderPnlMartRepository orderPnlMartRepository;

  public void refreshAfterEvent(
      long accountId,
      MarketplaceEvent event,
      String requestId
  ) {
    if (isOrderPnlRelated(event)) {
      log.info(
          "Refreshing order PnL mart (by account): requestId={}, accountId={}, event={}",
          requestId,
          accountId,
          event
      );

      orderPnlMartRepository.refresh(accountId);
    }
  }

  private boolean isOrderPnlRelated(MarketplaceEvent event) {
    return switch (event) {
      case SALES_FACT,
          FACT_LOGISTICS_COSTS,
          FACT_FINANCE,
          FACT_PENALTIES,
          FACT_COMMISSION -> true;
      default -> false;
    };
  }
}
