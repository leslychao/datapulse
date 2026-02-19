package io.datapulse.etl.v1.flow.core;

import io.datapulse.core.service.account.AccountService;
import io.datapulse.domain.response.account.AccountResponse;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.v1.dto.EtlRunRequest;
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
    return accountService.getActive()
        .stream()
        .map(AccountResponse::id)
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
