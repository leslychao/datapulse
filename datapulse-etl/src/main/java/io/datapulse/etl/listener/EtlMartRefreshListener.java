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

    long startedAt = System.currentTimeMillis();

    log.info(
        "ETL mart refresh START: requestId={}, accountId={}, event={}",
        event.requestId(),
        event.accountId(),
        marketplaceEvent
    );

    try {
      martRefreshService.refreshAfterEvent(
          event.accountId(),
          marketplaceEvent,
          event.requestId()
      );

      log.info(
          "ETL mart refresh SUCCESS: requestId={}, accountId={}, event={}",
          event.requestId(),
          event.accountId(),
          marketplaceEvent
      );

    } catch (Exception ex) {
      log.error(
          "ETL mart refresh ERROR: requestId={}, accountId={}, event={}",
          event.requestId(),
          event.accountId(),
          marketplaceEvent,
          ex
      );
      throw ex;
    } finally {
      long durationMs = System.currentTimeMillis() - startedAt;

      log.info(
          "ETL mart refresh FINISHED: requestId={}, accountId={}, event={}, durationMs={}",
          event.requestId(),
          event.accountId(),
          marketplaceEvent,
          durationMs
      );
    }
  }
}
