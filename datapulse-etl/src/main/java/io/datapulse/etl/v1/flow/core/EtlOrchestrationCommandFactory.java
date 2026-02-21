package io.datapulse.etl.v1.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_UNKNOWN;
import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.v1.dto.EtlDateRange;
import io.datapulse.etl.v1.dto.EtlRunRequest;
import io.datapulse.etl.v1.dto.RunTask;
import io.datapulse.etl.service.EtlDateRangeResolver;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class EtlOrchestrationCommandFactory {

  private final EtlDateRangeResolver dateRangeResolver;

  public EtlOrchestrationCommandFactory(EtlDateRangeResolver dateRangeResolver) {
    this.dateRangeResolver = dateRangeResolver;
  }

  public List<RunTask> toRunTasks(EtlRunRequest request) {
    if (request.accountId() == null || request.event() == null) {
      throw new AppException(ETL_REQUEST_INVALID, "accountId,event");
    }
    MarketplaceEvent event = MarketplaceEvent.fromString(request.event());
    if (event == null) {
      throw new AppException(ETL_EVENT_UNKNOWN, request.event());
    }
    EtlDateRange range = dateRangeResolver.resolve(event, request.dateMode(), request.dateFrom(), request.dateTo(), request.lastDays());
    int burst = request.burst() == null || request.burst() < 1 ? 1 : request.burst();
    return IntStream.range(0, burst)
        .mapToObj(index -> new RunTask(request.accountId(), event, range.dateFrom(), range.dateTo()))
        .toList();
  }
}
