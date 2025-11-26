package io.datapulse.etl.handler;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.normalized.OzonSalesAnalyticsRawDto;
import io.datapulse.marketplaces.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.marketplaces.mapper.OzonSalesAnalyticsRawMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OzonSalesFactRawBatchHandler implements EtlBatchHandler<OzonAnalyticsApiRaw> {

  private final RawBatchInsertJdbcRepository rawRepository;
  private final OzonSalesAnalyticsRawMapper mapper;

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
    List<OzonSalesAnalyticsRawDto> normalizedBatch = rawBatch.stream()
        .map(mapper::toDto)
        .toList();
    rawRepository.saveBatch(normalizedBatch, RawTableNames.OZON_SALES_FACT, requestId, snapshotId,
        accountId, marketplace);
  }
}
