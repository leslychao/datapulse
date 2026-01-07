package io.datapulse.etl.listener;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.scenario.EtlEventCompletedEvent;
import io.datapulse.etl.service.MartRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EtlMartRefreshListener {

  private final MartRefreshService martRefreshService;

  @EventListener
  public void onEventCompleted(EtlEventCompletedEvent event) {
    MarketplaceEvent marketplaceEvent = event.event();

    log.info(
        "Starting mart refresh after ETL materialization: requestId={}, accountId={}, event={}",
        event.requestId(),
        event.accountId(),
        marketplaceEvent
    );

    martRefreshService.refreshAfterEvent(
        event.accountId(),
        marketplaceEvent,
        event.requestId()
    );
  }
}
