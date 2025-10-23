package io.datapulse.domain.port;

import io.datapulse.domain.model.Account;
import io.datapulse.domain.model.Sale;
import java.util.List;

public interface PersistencePort {

  Account saveAccount(Account account);

  List<Account> findActiveAccounts();

  void upsertSalesBatch(List<Sale> salesBatch);
}
