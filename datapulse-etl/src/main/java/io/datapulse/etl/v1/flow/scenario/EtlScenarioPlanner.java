package io.datapulse.etl.v1.flow.scenario;

import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.v1.dto.EtlDateRange;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioEventConfig;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioRunRequest;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioStep;
import io.datapulse.etl.service.EtlDateRangeResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class EtlScenarioPlanner {

  private final EtlDateRangeResolver dateRangeResolver;

  public EtlScenarioPlanner(EtlDateRangeResolver dateRangeResolver) {
    this.dateRangeResolver = dateRangeResolver;
  }

  public List<EtlScenarioStep> buildSteps(
      EtlScenarioRunRequest request,
      String requestId
  ) {
    if (request.events() == null || request.events().isEmpty()) {
      throw new IllegalArgumentException("Scenario must contain at least one event config");
    }

    Map<MarketplaceEvent, EtlScenarioEventConfig> configByEvent = request.events()
        .stream()
        .collect(Collectors.toMap(
            EtlScenarioEventConfig::event,
            Function.identity(),
            (left, right) -> {
              throw new IllegalArgumentException(
                  "Duplicate event in scenario: " + left.event()
              );
            },
            LinkedHashMap::new
        ));

    Set<MarketplaceEvent> orderedRoots = new LinkedHashSet<>(configByEvent.keySet());
    List<MarketplaceEvent> orderedEvents = topologicallySortWithDependencies(orderedRoots);

    return orderedEvents.stream()
        .map(event -> toScenarioStep(request, requestId, event, configByEvent))
        .toList();
  }

  private EtlScenarioStep toScenarioStep(
      EtlScenarioRunRequest request,
      String requestId,
      MarketplaceEvent event,
      Map<MarketplaceEvent, EtlScenarioEventConfig> configByEvent
  ) {
    EtlScenarioEventConfig config = configByEvent.get(event);
    if (config == null) {
      throw new IllegalArgumentException(
          "Missing configuration for dependent event: " + event
      );
    }

    EtlDateRange range = dateRangeResolver.resolve(
        event,
        config.dateMode(),
        config.dateFrom(),
        config.dateTo(),
        config.lastDays()
    );

    return new EtlScenarioStep(
        requestId,
        request.accountId(),
        event,
        range.dateFrom(),
        range.dateTo()
    );
  }

  private List<MarketplaceEvent> topologicallySortWithDependencies(
      Set<MarketplaceEvent> roots
  ) {
    List<MarketplaceEvent> result = new ArrayList<>();
    Set<MarketplaceEvent> visited = new LinkedHashSet<>();
    Set<MarketplaceEvent> visiting = new LinkedHashSet<>();

    for (MarketplaceEvent root : roots) {
      dfs(root, visited, visiting, result);
    }

    return result;
  }

  private void dfs(
      MarketplaceEvent current,
      Set<MarketplaceEvent> visited,
      Set<MarketplaceEvent> visiting,
      List<MarketplaceEvent> result
  ) {
    if (visited.contains(current)) {
      return;
    }
    if (!visiting.add(current)) {
      throw new IllegalStateException(
          "Cyclic dependency in MarketplaceEvent graph at " + current
      );
    }

    for (MarketplaceEvent dependency : current.dependencies()) {
      dfs(dependency, visited, visiting, result);
    }

    visiting.remove(current);
    visited.add(current);
    result.add(current);
  }
}
