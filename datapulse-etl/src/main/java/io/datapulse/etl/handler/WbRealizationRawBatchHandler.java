package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.raw.wb.WbRealizationRaw;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbRealizationRawBatchHandler implements EtlBatchHandler<WbRealizationRaw> {

  private final RawBatchInsertJdbcRepository rawRepository;

  @Override
  public Class<WbRealizationRaw> elementType() {
    return WbRealizationRaw.class;
  }

  @Override
  public void handleBatch(
      List<WbRealizationRaw> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    rawRepository.saveBatch(rawBatch, RawTableNames.WB_REALIZATION, requestId, snapshotId,
        accountId, marketplace);
  }
}
