package io.datapulse.etl.flow.orchestrator;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.flow.core.model.EventWindow;
import io.datapulse.etl.flow.core.model.ExecutionDescriptor;
import io.datapulse.etl.flow.core.model.ExecutionPlan;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanFactory {

  private final AccountConnectionService accountConnectionService;
  private final EtlSourceRegistry etlSourceRegistry;

  public ExecutionPlanFactory(
      AccountConnectionService accountConnectionService,
      EtlSourceRegistry etlSourceRegistry
  ) {
    this.accountConnectionService = accountConnectionService;
    this.etlSourceRegistry = etlSourceRegistry;
  }

  public ExecutionPlan buildPlan(OrchestrationCommand command) {
    List<MarketplaceType> marketplaces = accountConnectionService
        .getActiveMarketplacesByAccountId(command.accountId());

    if (marketplaces.isEmpty()) {
      throw new AppException(
          MessageCodes.ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND,
          command.accountId()
      );
    }

    EventWindow window = new EventWindow(command.from(), command.to());
    List<ExecutionDescriptor> executions = marketplaces.stream()
        .flatMap(marketplace -> etlSourceRegistry.findSources(command.event(), marketplace).stream())
        .map(source -> toExecutionDescriptor(command, window, source))
        .toList();

    if (executions.isEmpty()) {
      throw new AppException(MessageCodes.ETL_EVENT_SOURCES_MISSING, command.event().name());
    }

    return new ExecutionPlan(command.requestId(), command.accountId(), window, executions);
  }

  private ExecutionDescriptor toExecutionDescriptor(
      OrchestrationCommand command,
      EventWindow window,
      RegisteredSource source
  ) {
    return new ExecutionDescriptor(
        command.requestId(),
        command.accountId(),
        command.event(),
        window,
        source.marketplace(),
        source.sourceId(),
        Objects.requireNonNull(source.rawTable())
    );
  }
}
