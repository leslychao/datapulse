package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseListRaw;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonWarehouseListRawBatchHandler implements EtlBatchHandler<OzonWarehouseListRaw> {

  private final RawBatchInsertJdbcRepository rawRepository;

  @Override
  public Class<OzonWarehouseListRaw> elementType() {
    return OzonWarehouseListRaw.class;
  }

  @Override
  public void handleBatch(
      List<OzonWarehouseListRaw> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    rawRepository.saveBatch(
        rawBatch,
        RawTableNames.OZON_WAREHOUSE_LIST,
        requestId,
        snapshotId,
        accountId,
        marketplace
    );
  }
}
