package io.datapulse.etl.repository.wb;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.wb.WbRealizationRaw;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class WbRealizationRawJdbcRepository {

  private static final String TABLE_NAME = "raw_realization_fact_wb";

  private final RawBatchInsertJdbcRepository delegate;

  public void saveBatch(
      List<WbRealizationRaw> batch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    delegate.saveBatch(batch, TABLE_NAME, requestId, snapshotId, accountId, marketplace);
  }
}
