package io.datapulse.etl.flow.batch;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.ozon.OzonProductInfoRaw;
import io.datapulse.etl.flow.batch.repository.OzonProductInfoRawJdbcRepository;
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
      Long accountId,
      MarketplaceType marketplace
  ) {
    rawRepository.saveBatch(rawBatch, accountId, marketplace);
  }
}
