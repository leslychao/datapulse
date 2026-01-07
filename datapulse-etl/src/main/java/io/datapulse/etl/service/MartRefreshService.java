package io.datapulse.etl.service;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.repository.OrderPnlMartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MartRefreshService {

  private final OrderPnlMartRepository orderPnlMartRepository;

  public void refreshAfterEvent(
      long accountId,
      MarketplaceEvent event,
      String requestId
  ) {
    if (isOrderPnlRelated(event)) {
      log.info(
          "Refreshing order PnL mart: requestId={}, accountId={}, event={}",
          requestId, accountId, event
      );

      orderPnlMartRepository.refresh(accountId, requestId);
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
