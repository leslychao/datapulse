package io.datapulse.etl.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchStreamProcessor {

  public <S, T> void process(
      String sourceName,
      Supplier<Stream<S>> streamSupplier,
      Function<S, T> mapper,
      Consumer<Collection<T>> batchSaver,
      int batchSize
  ) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }

    Stream<S> sourceStream = streamSupplier.get();
    if (sourceStream == null) {
      throw new IllegalStateException("streamSupplier must not return null");
    }

    try (Stream<S> stream = sourceStream.sequential()) {
      List<T> buffer = new ArrayList<>(batchSize);

      stream.forEach(sourceItem -> {
        T mappedItem;
        try {
          mappedItem = mapper.apply(sourceItem);
        } catch (RuntimeException ex) {
          log.warn(
              "Failed to map source item from {}. raw={}",
              sourceName,
              sourceItem,
              ex
          );
          return;
        }

        if (mappedItem == null) {
          return;
        }

        buffer.add(mappedItem);
        if (buffer.size() == batchSize) {
          flushBatch(sourceName, buffer, batchSaver);
        }
      });

      if (!buffer.isEmpty()) {
        flushBatch(sourceName, buffer, batchSaver);
      }
    }
  }

  private <T> void flushBatch(
      String sourceName,
      List<T> buffer,
      Consumer<Collection<T>> batchSaver
  ) {
    List<T> batch = List.copyOf(buffer);
    buffer.clear();

    batchSaver.accept(batch);

    if (log.isDebugEnabled()) {
      log.debug(
          "Saved batch of {} items from source {}",
          batch.size(),
          sourceName
      );
    }
  }
}
