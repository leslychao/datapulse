package io.datapulse.integration.domain;

import io.datapulse.integration.domain.event.ConnectionCreatedEvent;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionValidationListener {

  private final ConnectionValidationService validationService;

  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onConnectionCreated(ConnectionCreatedEvent event) {
    log.info("Triggering validation after connection created: connectionId={}",
        event.connectionId());
    validationService.validateAsync(event.connectionId());
  }

  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onConnectionStatusChanged(ConnectionStatusChangedEvent event) {
    if (ConnectionStatus.PENDING_VALIDATION.name().equals(event.newStatus())) {
      log.info("Triggering validation after status change: connectionId={}, trigger={}",
          event.connectionId(), event.trigger());
      validationService.validateAsync(event.connectionId());
    }
  }
}
