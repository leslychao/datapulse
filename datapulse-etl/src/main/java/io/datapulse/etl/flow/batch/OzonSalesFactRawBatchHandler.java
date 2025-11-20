package io.datapulse.etl.flow.batch;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.etl.flow.batch.repository.OzonSalesFactRawJdbcRepository;
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
      Long accountId,
      MarketplaceType marketplace
  ) {
    rawRepository.saveBatch(rawBatch, accountId, marketplace);
  }
}
