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

  private Map<Class<?>, EtlBatchHandler<?>> handlersByType;

  @PostConstruct
  void init() {
    Map<Class<?>, EtlBatchHandler<?>> map = new HashMap<>();
    for (EtlBatchHandler<?> handler : handlerList) {
      Class<?> elementType = handler.elementType();
      EtlBatchHandler<?> previous = map.put(elementType, handler);
      if (previous != null) {
        throw new AppException(ETL_BATCH_HANDLER_DUPLICATE, elementType.getName());
      }
    }
    this.handlersByType = Map.copyOf(map);
  }

  public void dispatch(
      List<?> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    if (rawBatch.isEmpty()) {
      return;
    }

    Object first = rawBatch.get(0);
    Class<?> actualType = first.getClass();

    EtlBatchHandler<?> handler = handlersByType.get(actualType);
    if (handler == null) {
      throw new AppException(ETL_BATCH_HANDLER_NOT_FOUND, actualType.getName());
    }

    Class<?> expectedType = handler.elementType();
    if (!expectedType.isInstance(first)) {
      throw new AppException(
          ETL_BATCH_HANDLER_TYPE_MISMATCH,
          expectedType.getName(),
          expectedType.getName(),
          actualType.getName()
      );
    }

    dispatchTyped(handler, rawBatch, requestId, snapshotId, accountId, marketplace);
  }

  private <T> void dispatchTyped(
      EtlBatchHandler<T> handler,
      List<?> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    Class<T> elementType = handler.elementType();
    List<T> typedBatch = rawBatch.stream()
        .map(elementType::cast)
        .toList();

    handler.handleBatch(typedBatch, requestId, snapshotId, accountId, marketplace);
  }
}
