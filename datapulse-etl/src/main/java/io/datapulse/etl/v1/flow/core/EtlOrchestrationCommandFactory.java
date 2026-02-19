package io.datapulse.etl.v1.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_UNKNOWN;
import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.v1.dto.EtlDateMode;
import io.datapulse.etl.v1.dto.EtlDateRange;
import io.datapulse.etl.v1.dto.OrchestrationCommand;
import io.datapulse.etl.service.EtlDateRangeResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class EtlOrchestrationCommandFactory {

  private record RequiredField(String name, Object value) {

  }

  private final EtlDateRangeResolver dateRangeResolver;

  public EtlOrchestrationCommandFactory(EtlDateRangeResolver dateRangeResolver) {
    this.dateRangeResolver = dateRangeResolver;
  }

  public OrchestrationCommand toCommand(
      String requestId,
      Long accountId,
      String eventCode,
      EtlDateMode dateMode,
      LocalDate dateFrom,
      LocalDate dateTo,
      Integer lastDays,
      List<String> sourceIds
  ) {
    validateRequiredFields(accountId, eventCode);

    MarketplaceEvent event = MarketplaceEvent.fromString(eventCode);
    if (event == null) {
      throw new AppException(ETL_EVENT_UNKNOWN, eventCode);
    }

    EtlDateRange range = dateRangeResolver.resolve(
        event,
        dateMode,
        dateFrom,
        dateTo,
        lastDays
    );

    String effectiveRequestId = resolveRequestId(requestId);

    List<String> safeSourceIds =
        sourceIds == null ? List.of() : List.copyOf(sourceIds);

    return new OrchestrationCommand(
        effectiveRequestId,
        accountId,
        event,
        range.dateFrom(),
        range.dateTo(),
        safeSourceIds
    );
  }

  private String resolveRequestId(String requestId) {
    return (requestId == null || requestId.isBlank())
        ? UUID.randomUUID().toString()
        : requestId;
  }

  private void validateRequiredFields(Long accountId, String eventCode) {
    List<String> missing = Stream.of(
            new RequiredField("accountId", accountId),
            new RequiredField("event", eventCode)
        )
        .filter(f -> f.value() == null)
        .map(RequiredField::name)
        .toList();

    if (!missing.isEmpty()) {
      throw new AppException(ETL_REQUEST_INVALID, String.join(", ", missing));
    }
  }
}
