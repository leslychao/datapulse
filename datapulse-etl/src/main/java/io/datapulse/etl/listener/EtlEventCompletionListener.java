package io.datapulse.etl.listener;

import io.datapulse.etl.dto.scenario.EtlEventCompletedEvent;
import io.datapulse.etl.cache.EtlEventCompletionCache;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlEventCompletionListener {

  private final EtlEventCompletionCache completionCache;

  @EventListener
  public void onEventCompleted(EtlEventCompletedEvent event) {
    completionCache.markCompleted(event.accountId(), event.event());
  }
}
