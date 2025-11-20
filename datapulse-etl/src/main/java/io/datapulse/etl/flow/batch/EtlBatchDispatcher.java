package io.datapulse.etl.flow.batch;

import static io.datapulse.domain.MessageCodes.ETL_BATCH_HANDLER_DUPLICATE;
import static io.datapulse.domain.MessageCodes.ETL_BATCH_HANDLER_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ETL_BATCH_HANDLER_TYPE_MISMATCH;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlBatchDispatcher {

  private final List<EtlBatchHandler<?>> handlerList;

  private Map<String, EtlBatchHandler<?>> handlersByRawTable;

  @PostConstruct
  void init() {
    Map<String, EtlBatchHandler<?>> map = new HashMap<>();
    for (EtlBatchHandler<?> handler : handlerList) {
      String rawTableName = handler.rawTableName();
      EtlBatchHandler<?> previous = map.put(rawTableName, handler);
      if (previous != null) {
        throw new AppException(ETL_BATCH_HANDLER_DUPLICATE, rawTableName);
      }
    }
    this.handlersByRawTable = Map.copyOf(map);
  }

  public void dispatch(
      String rawTable,
      List<?> rawBatch,
      Long accountId,
      MarketplaceType marketplace
  ) {
    EtlBatchHandler<?> handler = handlersByRawTable.get(rawTable);
    if (handler == null) {
      throw new AppException(ETL_BATCH_HANDLER_NOT_FOUND, rawTable);
    }

    if (rawBatch.isEmpty()) {
      return;
    }

    Class<?> elementType = handler.elementType();
    Object first = rawBatch.get(0);
    if (!elementType.isInstance(first)) {
      throw new AppException(
          ETL_BATCH_HANDLER_TYPE_MISMATCH,
          rawTable,
          elementType.getName(),
          first.getClass().getName()
      );
    }

    dispatchTyped(handler, rawBatch, accountId, marketplace);
  }

  private <T> void dispatchTyped(
      EtlBatchHandler<T> handler,
      List<?> rawBatch,
      Long accountId,
      MarketplaceType marketplace
  ) {
    Class<T> elementType = handler.elementType();

    List<T> typedBatch = rawBatch.stream()
        .map(elementType::cast)
        .toList();

    handler.handleBatch(typedBatch, accountId, marketplace);
  }
}
