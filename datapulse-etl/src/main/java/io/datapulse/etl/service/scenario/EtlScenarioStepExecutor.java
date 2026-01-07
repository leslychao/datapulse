package io.datapulse.etl.service.scenario;

import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.cache.EtlEventCompletionCache;
import io.datapulse.etl.dto.EtlDateMode;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.dto.scenario.EtlScenarioStep;
import io.datapulse.etl.flow.core.EtlOrchestrationCommandFactory;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlScenarioStepExecutor {

  private static final Duration DEFAULT_EVENT_TIMEOUT = Duration.ofMinutes(30);

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final EtlEventCompletionCache completionCache;
  private final EtlOrchestrationCommandFactory orchestrationCommandFactory;

  public void execute(EtlScenarioStep step) {
    awaitEvents(
        step.accountId(),
        step.event().dependencies(),
        "dependencies before event " + step.event()
    );

    OrchestrationCommand command = orchestrationCommandFactory.toCommand(
        step.requestId(),
        step.accountId(),
        step.event().name(),
        EtlDateMode.RANGE,
        step.dateFrom(),
        step.dateTo(),
        null,
        List.of()
    );

    log.info(
        "Submitting ETL event from scenario: requestId={}, accountId={}, event={}",
        command.requestId(),
        command.accountId(),
        command.event()
    );

    etlExecutionRabbitTemplate.convertAndSend(
        EXCHANGE_EXECUTION,
        ROUTING_KEY_EXECUTION,
        command
    );

    awaitEvents(
        step.accountId(),
        Set.of(step.event()),
        "completion of event " + step.event()
    );
  }

  private void awaitEvents(
      long accountId,
      Set<MarketplaceEvent> events,
      String description
  ) {
    if (events.isEmpty()) {
      return;
    }
    try {
      completionCache
          .completionFutureForAll(accountId, events)
          .get(DEFAULT_EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Timeout or error while waiting for " + description + ", accountId=" + accountId,
          ex
      );
    }
  }
}
