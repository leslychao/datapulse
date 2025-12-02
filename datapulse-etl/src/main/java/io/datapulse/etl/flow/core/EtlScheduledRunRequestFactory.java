package io.datapulse.etl.flow.core;

import io.datapulse.core.service.AccountService;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlRunRequest;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlScheduledRunRequestFactory {

  private final AccountService accountService;

  public List<EtlRunRequest> buildDailyRunRequests(MarketplaceEvent event) {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    LocalDate today = LocalDate.now();
    return buildRunRequests(event, yesterday, today);
  }

  public List<EtlRunRequest> buildRunRequests(
      MarketplaceEvent event,
      LocalDate from,
      LocalDate to
  ) {
    return accountService.streamActive()
        .map(AccountDto::getId)
        .map(accountId -> new EtlRunRequest(
            accountId,
            event.name(),
            from,
            to,
            1
        ))
        .toList();
  }
}
