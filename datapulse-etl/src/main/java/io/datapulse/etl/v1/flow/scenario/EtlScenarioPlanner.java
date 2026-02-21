package io.datapulse.etl.v1.flow.scenario;

import io.datapulse.etl.v1.dto.EtlDateRange;
import io.datapulse.etl.v1.dto.RunTask;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioEventConfig;
import io.datapulse.etl.v1.dto.scenario.EtlScenarioRunRequest;
import io.datapulse.etl.service.EtlDateRangeResolver;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EtlScenarioPlanner {

  private final EtlDateRangeResolver dateRangeResolver;

  public EtlScenarioPlanner(EtlDateRangeResolver dateRangeResolver) {
    this.dateRangeResolver = dateRangeResolver;
  }

  public List<RunTask> buildRunTasks(EtlScenarioRunRequest request) {
    return request.events().stream().map(eventConfig -> toRunTask(request.accountId(), eventConfig)).toList();
  }

  private RunTask toRunTask(long accountId, EtlScenarioEventConfig config) {
    EtlDateRange range = dateRangeResolver.resolve(
        config.event(), config.dateMode(), config.dateFrom(), config.dateTo(), config.lastDays());
    return new RunTask(accountId, config.event(), range.dateFrom(), range.dateTo());
  }
}
