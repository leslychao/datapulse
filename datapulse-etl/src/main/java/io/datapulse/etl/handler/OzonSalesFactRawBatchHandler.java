package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.etl.repository.ozon.OzonSalesFactRawJdbcRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonSalesFactRawBatchHandler implements EtlBatchHandler<OzonAnalyticsApiRaw> {

  private final OzonSalesFactRawJdbcRepository rawRepository;

  @Override
  public Class<OzonAnalyticsApiRaw> elementType() {
    return OzonAnalyticsApiRaw.class;
  }

  @Override
  public void handleBatch(
      List<OzonAnalyticsApiRaw> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    rawRepository.saveBatch(rawBatch, requestId, snapshotId, accountId, marketplace);
  }
}
