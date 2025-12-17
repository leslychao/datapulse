package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_DATE_RANGE_INVALID;
import static io.datapulse.domain.MessageCodes.ETL_EVENT_UNKNOWN;
import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;
import static io.datapulse.domain.MessageCodes.REQUEST_REQUIRED;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlDateMode;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.OrchestrationCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class EtlOrchestrationCommandFactory {

  private static final int DEFAULT_LAST_DAYS = 30;

  private record RequiredField(
      String name,
      Object value
  ) {

  }

  private record EffectiveDateRange(
      LocalDate dateFrom,
      LocalDate dateTo
  ) {

  }

  public OrchestrationCommand toCommand(EtlRunRequest request) {
    validateRunRequest(request);

    MarketplaceEvent event = MarketplaceEvent.fromString(request.event());
    if (event == null) {
      throw new AppException(
          ETL_EVENT_UNKNOWN,
          request.event()
      );
    }

    EffectiveDateRange range = resolveDateRange(event, request);

    String requestId = UUID.randomUUID().toString();

    List<String> sourceIds = request.sourceIds() == null
        ? List.of()
        : List.copyOf(request.sourceIds());

    return new OrchestrationCommand(
        requestId,
        request.accountId(),
        event,
        range.dateFrom(),
        range.dateTo(),
        sourceIds
    );
  }

  private void validateRunRequest(EtlRunRequest request) {
    if (request == null) {
      throw new AppException(
          REQUEST_REQUIRED,
          "EtlRunRequest"
      );
    }

    List<String> missingFields = Stream.of(
            new RequiredField("accountId", request.accountId()),
            new RequiredField("event", request.event())
        )
        .filter(field -> field.value() == null)
        .map(RequiredField::name)
        .toList();

    if (!missingFields.isEmpty()) {
      throw new AppException(
          ETL_REQUEST_INVALID,
          String.join(", ", missingFields)
      );
    }
  }

  private EffectiveDateRange resolveDateRange(
      MarketplaceEvent event,
      EtlRunRequest request
  ) {
    EtlDateMode mode = request.dateMode();
    LocalDate dateFrom = request.dateFrom();
    LocalDate dateTo = request.dateTo();

    if (mode == null) {
      if (dateFrom != null || dateTo != null) {
        mode = EtlDateMode.RANGE;
      } else {
        mode = defaultDateMode(event);
      }
    }

    return switch (mode) {
      case NONE -> new EffectiveDateRange(null, null);

      case RANGE -> {
        if (dateFrom == null || dateTo == null) {
          throw new AppException(
              ETL_REQUEST_INVALID,
              "dateFrom,dateTo"
          );
        }

        if (dateFrom.isAfter(dateTo)) {
          throw new AppException(
              ETL_DATE_RANGE_INVALID,
              dateFrom,
              dateTo
          );
        }

        yield new EffectiveDateRange(dateFrom, dateTo);
      }

      case LAST_DAYS -> {
        int effectiveDays = resolveLastDays(request.lastDays());

        LocalDate today = LocalDate.now();
        LocalDate calculatedFrom = today.minusDays(effectiveDays);

        yield new EffectiveDateRange(calculatedFrom, today);
      }
    };
  }

  private EtlDateMode defaultDateMode(MarketplaceEvent event) {
    return EtlDateMode.NONE;
  }

  private int resolveLastDays(Integer requestedLastDays) {
    if (requestedLastDays != null && requestedLastDays > 0) {
      return requestedLastDays;
    }
    return DEFAULT_LAST_DAYS;
  }
}
