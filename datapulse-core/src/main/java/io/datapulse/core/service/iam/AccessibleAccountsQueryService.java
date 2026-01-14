package io.datapulse.core.service.iam;

import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.core.repository.account.AccountRepository;
import io.datapulse.domain.response.account.AccountResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessibleAccountsQueryService {

  private final AccountRepository accountRepository;
  private final MapperFacade mapperFacade;

  @Transactional(readOnly = true)
  public List<AccountResponse> findAccessibleActiveAccounts(long profileId) {
    return accountRepository.findAccessibleActiveAccountsForProfileId(profileId)
        .stream()
        .map(entity -> mapperFacade.to(entity, AccountResponse.class))
        .toList();
  }
}
