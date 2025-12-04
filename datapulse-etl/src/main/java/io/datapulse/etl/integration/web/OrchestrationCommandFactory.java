package io.datapulse.etl.integration.web;

import io.datapulse.etl.integration.messaging.OrchestrationCommand;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OrchestrationCommandFactory {

  public OrchestrationCommand toCommand(EtlRunRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }
    return new OrchestrationCommand(
        request.eventId(),
        request.source(),
        request.payloadReference(),
        Instant.now()
    );
  }
}
