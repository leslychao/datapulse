package io.datapulse.etl.dim.warehouse;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WarehouseMaterializationHandler {

  private static final int BATCH_SIZE = 500;

  private final WarehouseNormalizer normalizer;
  private final DimWarehouseRepository repository;
  private final WarehouseRawReader rawReader;

  public void materialize(Long accountId, String requestId) {
    try (var ozonFbsStream = rawReader.streamOzonFbs(accountId, requestId)) {
      processStream(ozonFbsStream, normalizer::fromOzonFbs);
    }

    try (var ozonFboStream = rawReader.streamOzonFboWarehouses(accountId, requestId)) {
      processStream(ozonFboStream, normalizer::fromOzonFbo);
    }

    try (var wbFbwStream = rawReader.streamWbFbw(accountId, requestId)) {
      processStream(wbFbwStream, normalizer::fromWbFbw);
    }

    try (var wbFbsOfficesStream = rawReader.streamWbFbsOffices(accountId, requestId)) {
      processStream(wbFbsOfficesStream, normalizer::fromWbFbsOffice);
    }

    try (var wbSellerStream = rawReader.streamWbSellerWarehouses(accountId, requestId)) {
      processStream(wbSellerStream, normalizer::fromWbSeller);
    }
  }

  private <T> void processStream(
      java.util.stream.Stream<T> stream,
      java.util.function.Function<T, DimWarehouse> mapper
  ) {
    List<DimWarehouse> buffer = new ArrayList<>(BATCH_SIZE);

    stream.forEach(raw -> {
      DimWarehouse dimWarehouse = mapper.apply(raw);
      if (dimWarehouse == null) {
        return;
      }

      buffer.add(dimWarehouse);
      if (buffer.size() >= BATCH_SIZE) {
        repository.saveAll(buffer);
        buffer.clear();
      }
    });

    if (!buffer.isEmpty()) {
      repository.saveAll(buffer);
    }
  }
}
