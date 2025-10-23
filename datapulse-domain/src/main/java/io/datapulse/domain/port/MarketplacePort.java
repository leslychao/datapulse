package io.datapulse.domain.port;

import io.datapulse.domain.model.Sale;
import java.time.LocalDate;
import java.util.List;

public interface MarketplacePort {

  List<Sale> fetchSales(Long accountId, LocalDate fromInclusive, LocalDate toInclusive);
}
