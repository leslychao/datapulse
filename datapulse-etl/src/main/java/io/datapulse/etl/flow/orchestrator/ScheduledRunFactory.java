package io.datapulse.etl.flow.orchestrator;

import io.datapulse.core.service.AccountService;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScheduledRunFactory {

  private final AccountService accountService;

  public ScheduledRunFactory(AccountService accountService) {
    this.accountService = accountService;
  }

  public List<EtlRunRequest> buildDailyRequests(MarketplaceEvent event) {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    LocalDate today = LocalDate.now();
    return buildRequests(event, yesterday, today);
  }

  public List<EtlRunRequest> buildRequests(MarketplaceEvent event, LocalDate from, LocalDate to) {
    return accountService.streamActive()
        .map(AccountDto::getId)
        .map(accountId -> new EtlRunRequest(accountId, event.name(), from, to, 1))
        .toList();
  }
}
