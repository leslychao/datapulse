package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.raw.ozon.OzonProductInfoRaw;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonProductInfoRawBatchHandler implements EtlBatchHandler<OzonProductInfoRaw> {

  private final RawBatchInsertJdbcRepository rawRepository;

  @Override
  public Class<OzonProductInfoRaw> elementType() {
    return OzonProductInfoRaw.class;
  }

  @Override
  public void handleBatch(
      List<OzonProductInfoRaw> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    rawRepository.saveBatch(rawBatch, RawTableNames.OZON_PRODUCT_INFO, requestId, snapshotId,
        accountId, marketplace);
  }
}
