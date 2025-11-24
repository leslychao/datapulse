package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.ozon.OzonProductInfoRaw;
import io.datapulse.etl.repository.ozon.OzonProductInfoRawJdbcRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonProductInfoRawBatchHandler implements EtlBatchHandler<OzonProductInfoRaw> {

  private final OzonProductInfoRawJdbcRepository rawRepository;

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
    rawRepository.saveBatch(rawBatch, requestId, snapshotId, accountId, marketplace);
  }
}
