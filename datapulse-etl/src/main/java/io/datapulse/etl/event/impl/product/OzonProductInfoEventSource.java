package io.datapulse.etl.event.impl.product;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.etl.repository.jdbc.RawOzonProductRepository;
import io.datapulse.marketplaces.adapter.OzonAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.product.OzonProductInfoItemRaw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    events = {MarketplaceEvent.PRODUCT_DICT},
    marketplace = MarketplaceType.OZON,
    rawTableName = RawTableNames.RAW_OZON_PRODUCT_INFO,
    order = 1
)
public class OzonProductInfoEventSource implements EventSource {

  private static final int BATCH_SIZE = 1_000;

  private final OzonAdapter ozonAdapter;
  private final RawOzonProductRepository rawOzonProductRepository;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Long> productIds = rawOzonProductRepository.fetchAllProductIds(accountId);
    if (productIds.isEmpty()) {
      log.info("Ozon product info: no productIds found, nothing to load. accountId={}", accountId);
      return Collections.emptyList();
    }

    int totalSize = productIds.size();
    int batchCount = batchCount(totalSize, BATCH_SIZE);

    log.info(
        "Ozon product info load started. accountId={}, totalProducts={}, batches={}, batchSize={}",
        accountId, totalSize, batchCount, BATCH_SIZE
    );

    List<Snapshot<?>> snapshots = new ArrayList<>(batchCount);

    IntStream.range(0, batchCount)
        .forEach(batchIndex -> {
          int fromIndex = batchIndex * BATCH_SIZE;
          int toIndex = Math.min(fromIndex + BATCH_SIZE, totalSize);
          List<Long> batchIds = productIds.subList(fromIndex, toIndex);

          Snapshot<OzonProductInfoItemRaw> snapshot = downloadBatch(accountId, batchIndex,
              batchIds);
          snapshots.add(snapshot);
        });

    log.info(
        "Ozon product info load completed. accountId={}, totalSnapshots={}",
        accountId, snapshots.size()
    );

    return List.copyOf(snapshots);
  }

  private Snapshot<OzonProductInfoItemRaw> downloadBatch(
      long accountId,
      int batchIndex,
      List<Long> batchIds
  ) {
    try {
      log.debug(
          "Ozon product info batch download started. accountId={}, batchIndex={}, batchSize={}, firstProductId={}, lastProductId={}",
          accountId,
          batchIndex,
          batchIds.size(),
          batchIds.get(0),
          batchIds.get(batchIds.size() - 1)
      );

      Snapshot<OzonProductInfoItemRaw> snapshot =
          ozonAdapter.downloadProductInfoListBatch(accountId, batchIds);

      log.debug(
          "Ozon product info batch download completed. accountId={}, batchIndex={}",
          accountId, batchIndex
      );

      return snapshot;
    } catch (RuntimeException exception) {
      log.error(
          "Ozon product info batch download failed. accountId={}, batchIndex={}, batchSize={}",
          accountId, batchIndex, batchIds.size(), exception
      );
      throw exception;
    }
  }

  private static int batchCount(int totalSize, int batchSize) {
    if (totalSize <= 0) {
      return 0;
    }
    return (totalSize + batchSize - 1) / batchSize;
  }
}
