package io.datapulse.etl.nextgen.service;

import io.datapulse.etl.nextgen.dto.EventCommand;
import io.datapulse.etl.nextgen.dto.ExecutionCommand;
import io.datapulse.etl.nextgen.dto.MarketplaceScope;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanFactory {

  public List<ExecutionCommand> plan(EventCommand command) {
    return command.marketplaces().stream()
        .flatMap(scope -> buildScopePlan(command.eventId(), scope).stream())
        .toList();
  }

  private List<ExecutionCommand> buildScopePlan(String eventId, MarketplaceScope scope) {
    List<String> sources = scope.sourceKeys();
    return IntStream.range(0, sources.size())
        .mapToObj(order -> new ExecutionCommand(
            UUID.randomUUID(),
            eventId,
            sources.get(order),
            scope.marketplace(),
            order,
            0
        ))
        .toList();
  }
}
