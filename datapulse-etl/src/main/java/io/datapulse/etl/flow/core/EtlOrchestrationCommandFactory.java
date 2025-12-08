package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.OrchestrationCommand;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class EtlOrchestrationCommandFactory {

  private record RequiredField(
      String name,
      Object value
  ) {

  }

  public OrchestrationCommand toCommand(EtlRunRequest request) {
    validateRunRequest(request);

    MarketplaceEvent event = MarketplaceEvent.fromString(request.event());
    if (event == null) {
      throw new AppException(
          MessageCodes.ETL_REQUEST_INVALID,
          "event=" + request.event()
      );
    }

    String requestId = UUID.randomUUID().toString();

    return new OrchestrationCommand(
        requestId,
        request.accountId(),
        event,
        request.dateFrom(),
        request.dateTo(),
        List.of()
    );
  }

  private void validateRunRequest(EtlRunRequest request) {
    if (request == null) {
      throw new AppException(ETL_REQUEST_INVALID, "request is null");
    }
    List<String> missingFields = Stream.of(
            new RequiredField("accountId", request.accountId()),
            new RequiredField("event", request.event()),
            new RequiredField("dateFrom", request.dateFrom()),
            new RequiredField("dateTo", request.dateTo())
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

    if (request.dateFrom().isAfter(request.dateTo())) {
      throw new AppException(
          ETL_REQUEST_INVALID,
          "'from' must be <= 'to'"
      );
    }
  }
}
