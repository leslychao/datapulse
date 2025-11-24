package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.wb.WbRealizationRaw;
import io.datapulse.etl.repository.wb.WbRealizationRawJdbcRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbRealizationRawBatchHandler implements EtlBatchHandler<WbRealizationRaw> {

  private final WbRealizationRawJdbcRepository rawRepository;

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
    rawRepository.saveBatch(rawBatch, requestId, snapshotId, accountId, marketplace);
  }
}
