package io.datapulse.etl.repository.ozon;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OzonSalesFactRawJdbcRepository {

  private final RawBatchInsertJdbcRepository delegate;

  public void saveBatch(
      List<OzonAnalyticsApiRaw> batch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    delegate.saveBatch(
        batch,
        RawTableNames.OZON_SALES_FACT,
        requestId,
        snapshotId,
        accountId,
        marketplace);
  }
}
