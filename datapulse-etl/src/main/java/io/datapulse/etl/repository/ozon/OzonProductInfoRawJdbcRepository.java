package io.datapulse.etl.repository.ozon;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.ozon.OzonProductInfoRaw;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OzonProductInfoRawJdbcRepository {

  private final RawBatchInsertJdbcRepository delegate;

  public void saveBatch(
      List<OzonProductInfoRaw> batch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    delegate.saveBatch(
        batch,
        RawTableNames.OZON_PRODUCT_INFO,
        requestId,
        snapshotId,
        accountId,
        marketplace);
  }
}
