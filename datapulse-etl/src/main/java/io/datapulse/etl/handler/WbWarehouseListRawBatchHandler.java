package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseListRaw;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbWarehouseListRawBatchHandler implements EtlBatchHandler<WbWarehouseListRaw> {

  private final RawBatchInsertJdbcRepository rawRepository;

  @Override
  public Class<WbWarehouseListRaw> elementType() {
    return WbWarehouseListRaw.class;
  }

  @Override
  public void handleBatch(
      List<WbWarehouseListRaw> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    rawRepository.saveBatch(
        rawBatch,
        RawTableNames.WB_WAREHOUSE_LIST,
        requestId,
        snapshotId,
        accountId,
        marketplace
    );
  }
}
