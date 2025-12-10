package io.datapulse.etl.materialization.dim.warehouse;

import io.datapulse.etl.common.BatchStreamProcessor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseMaterializationHandler {

  private static final int BATCH_SIZE = 500;

  private final WarehouseNormalizer normalizer;
  private final DimWarehouseRepository repository;
  private final WarehouseRawReader rawReader;
  private final BatchStreamProcessor batchStreamProcessor;

  public void materialize(
      Long accountId,
      String requestId
  ) {
    process(
        "ozon_fbs",
        () -> rawReader.streamOzonFbs(accountId, requestId),
        normalizer::fromOzonFbs
    );

    process(
        "ozon_fbo",
        () -> rawReader.streamOzonFboWarehouses(accountId, requestId),
        normalizer::fromOzonFbo
    );

    process(
        "wb_fbw",
        () -> rawReader.streamWbFbw(accountId, requestId),
        normalizer::fromWbFbw
    );

    process(
        "wb_fbs_offices",
        () -> rawReader.streamWbFbsOffices(accountId, requestId),
        normalizer::fromWbFbsOffice
    );

    process(
        "wb_seller",
        () -> rawReader.streamWbSellerWarehouses(accountId, requestId),
        normalizer::fromWbSeller
    );
  }

  private <T> void process(
      String sourceName,
      Supplier<Stream<T>> streamSupplier,
      Function<T, DimWarehouse> mapper
  ) {
    batchStreamProcessor.process(
        sourceName,
        streamSupplier,
        mapper,
        repository::saveAll,
        BATCH_SIZE
    );
  }
}
