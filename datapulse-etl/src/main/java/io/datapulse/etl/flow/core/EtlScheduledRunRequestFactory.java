package io.datapulse.etl.flow.core;

import io.datapulse.core.service.AccountService;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlScheduledRunRequestFactory {

  private final AccountService accountService;

  public List<EtlRunRequest> buildDailyRunRequests(MarketplaceEvent event) {
    return buildRunRequests(event);
  }

  public List<EtlRunRequest> buildRunRequests(MarketplaceEvent event) {
    return accountService.streamActive()
        .map(AccountDto::getId)
        .map(accountId -> new EtlRunRequest(
            accountId,
            event.name(),
            null,
            null,
            null,
            null,
            1,
            List.of()
        ))
        .toList();
  }
}
