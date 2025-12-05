package io.datapulse.etl.flow.orchestrator;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.OrchestrationCommand;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class OrchestrationCommandMapper {

  private record RequiredField(String name, Object value) {
  }

  public OrchestrationCommand fromRequest(EtlRunRequest request) {
    validate(request);
    MarketplaceEvent event = MarketplaceEvent.fromString(request.event());
    if (event == null) {
      throw new AppException(ETL_REQUEST_INVALID, "event=" + request.event());
    }
    return new OrchestrationCommand(
        UUID.randomUUID().toString(),
        request.accountId(),
        event,
        request.from(),
        request.to()
    );
  }

  private void validate(EtlRunRequest request) {
    List<String> missing = Stream.of(
            new RequiredField("accountId", request.accountId()),
            new RequiredField("event", request.event()),
            new RequiredField("from", request.from()),
            new RequiredField("to", request.to())
        )
        .filter(field -> field.value() == null)
        .map(RequiredField::name)
        .toList();

    if (!missing.isEmpty()) {
      throw new AppException(ETL_REQUEST_INVALID, String.join(", ", missing));
    }
    if (request.from().isAfter(request.to())) {
      throw new AppException(ETL_REQUEST_INVALID, "'from' must be <= 'to'");
    }
  }
}
