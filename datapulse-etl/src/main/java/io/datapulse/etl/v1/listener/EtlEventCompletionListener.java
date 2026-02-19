package io.datapulse.etl.v1.listener;

import io.datapulse.etl.v1.dto.scenario.EtlEventCompletedEvent;
import io.datapulse.etl.v1.cache.EtlEventCompletionCache;
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
