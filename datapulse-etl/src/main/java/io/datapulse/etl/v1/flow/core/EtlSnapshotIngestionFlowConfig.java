package io.datapulse.etl.v1.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_INGEST_JSON_LAYOUT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_ELEMENT_TYPE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_FILE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_REQUIRED;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository;
import io.datapulse.etl.v1.dto.EtlIngestExecutionContext;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import io.datapulse.etl.v1.file.SnapshotIteratorFactory;
import io.datapulse.etl.v1.file.SnapshotRowIterator;
import io.datapulse.etl.v1.file.locator.JsonArrayLocator;
import io.datapulse.etl.v1.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.marketplaces.dto.Snapshot;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.aggregator.CorrelationStrategy;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.splitter.DefaultMessageSplitter;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.util.CollectionUtils;

@Configuration
@RequiredArgsConstructor
public class EtlSnapshotIngestionFlowConfig {

  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private static final String HDR_TOTAL_SNAPSHOTS = "etl.ingest.totalSnapshots";
  private static final String HDR_EXEC_CTX = "etl.ingest.execCtx";

  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final RawBatchInsertJdbcRepository repository;

  /**
   * Subflow, который можно вызывать как .gateway(ingestSubflow()). ВАЖНО: он возвращает тот же
   * EtlIngestExecutionContext, который пришёл на вход, но ТОЛЬКО после того как обработаны все
   * snapshots.
   */
  @Bean
  public IntegrationFlow ingestSubflow() {
    return f -> f
        // вход: EtlIngestExecutionContext

        // fetch snapshots -> List<SnapshotIngestContext>
        .transform(EtlIngestExecutionContext.class, this::fetchSnapshotsAndBuildSnapshotContexts)

        // сохраним "сколько snapshots всего" + оригинальный execution-context в headers
        .enrichHeaders(h -> h
            .headerFunction(HDR_TOTAL_SNAPSHOTS, m -> ((List<?>) m.getPayload()).size())
            .headerFunction(HDR_EXEC_CTX, m -> m.getHeaders().get(HDR_EXEC_CTX))
        )

        // split snapshots
        .split(new DefaultMessageSplitter())

        // 1 snapshot -> iterator rows (SnapshotRow)
        .split(SnapshotIngestContext.class, this::toSnapshotRowIterator)

        // rows -> batches (RawInsertBatch)
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message -> ((SnapshotRow<?>) message.getPayload()).context().key())
            .releaseStrategy(group ->
                group.size() >= SNAPSHOT_BATCH_SIZE
                    || group.getMessages().stream()
                    .map(m -> (SnapshotRow<?>) m.getPayload())
                    .anyMatch(SnapshotRow::last)
            )
            .outputProcessor(group -> {
              SnapshotRow<?> sample = (SnapshotRow<?>) group.getOne().getPayload();
              boolean lastBatch = group.getMessages().stream()
                  .map(m -> (SnapshotRow<?>) m.getPayload())
                  .anyMatch(SnapshotRow::last);

              List<?> rows = group.getMessages().stream()
                  .map(m -> ((SnapshotRow<?>) m.getPayload()).row())
                  .toList();

              return new RawInsertBatch<>(sample.context(), rows, lastBatch);
            })
            .expireGroupsUponCompletion(true)
        )

        // save batch; если это lastBatch для snapshot -> вернуть SnapshotIngestContext как "snapshot-completed signal"
        .handle(RawInsertBatch.class, (batch, headers) -> {
          saveBatch(batch);
          return batch.lastBatch() ? batch.context() : null;
        })
        .filter(p -> p != null)

        // fan-in: собрать "snapshot completed" по execution/source в 1 reply (original EtlIngestExecutionContext)
        .aggregate(aggregator -> aggregator
            .correlationStrategy((CorrelationStrategy) message -> {
              SnapshotIngestContext sctx = (SnapshotIngestContext) message.getPayload();
              EtlSourceExecution ex = sctx.execCtx().execution();
              return ex.requestId() + ":" + ex.sourceId();
            })
            .releaseStrategy(group -> {
              Integer total = (Integer) group.getOne().getHeaders().get(HDR_TOTAL_SNAPSHOTS);
              return total != null && group.size() >= total;
            })
            .outputProcessor(
                group -> (EtlIngestExecutionContext) group.getOne().getHeaders().get(HDR_EXEC_CTX))
            .expireGroupsUponCompletion(true)
        );
  }

  private List<SnapshotIngestContext> fetchSnapshotsAndBuildSnapshotContexts(
      EtlIngestExecutionContext execCtx) {
    EtlSourceExecution execution = execCtx.execution();
    RegisteredSource registeredSource = execCtx.registeredSource();

    List<Snapshot<?>> snapshots = registeredSource.source().fetchSnapshots(
        execution.accountId(),
        execution.event(),
        execution.dateFrom(),
        execution.dateTo()
    );

    if (CollectionUtils.isEmpty(snapshots)) {
      throw new AppException(ETL_INGEST_SNAPSHOT_REQUIRED);
    }

    return snapshots.stream().map(snapshot -> {
      if (snapshot == null) {
        throw new AppException(ETL_INGEST_SNAPSHOT_REQUIRED);
      }
      Path file = snapshot.file();
      if (file == null) {
        throw new AppException(ETL_INGEST_SNAPSHOT_FILE_REQUIRED);
      }
      Class<?> elementType = snapshot.elementType();
      if (elementType == null) {
        throw new AppException(ETL_INGEST_SNAPSHOT_ELEMENT_TYPE_REQUIRED);
      }
      return new SnapshotIngestContext(execCtx, file, elementType);
    }).toList();
  }

  private CloseableIterator<SnapshotRow<?>> toSnapshotRowIterator(
      SnapshotIngestContext snapshotCtx) {
    JsonArrayLocator locator = snapshotJsonLayoutRegistry.resolve(snapshotCtx.elementType());
    if (locator == null) {
      throw new AppException(ETL_INGEST_JSON_LAYOUT_NOT_FOUND, snapshotCtx.elementType());
    }

    CloseableIterator<?> delegate = snapshotIteratorFactory.createIterator(
        snapshotCtx.snapshotFile(),
        snapshotCtx.elementType(),
        locator
    );

    // ВАЖНО: IngestItemIterator у тебя уже существует.
    // Он возвращает (row, last) + context — мы просто используем наши типы.
    return new SnapshotRowIterator(delegate, snapshotCtx);
  }

  private void saveBatch(RawInsertBatch<?> batch) {
    if (batch.rows().isEmpty()) {
      return;
    }
    EtlSourceExecution execution = batch.context().execCtx().execution();
    RegisteredSource source = batch.context().execCtx().registeredSource();

    repository.saveBatch(
        batch.rows(),
        source.rawTable(),
        execution.requestId(),
        execution.accountId(),
        source.marketplace()
    );
  }

  /**
   * Execution-level context уже отдельным DTO: EtlIngestExecutionContext
   */

  /**
   * Snapshot-level unit of work.
   */
  public record SnapshotIngestContext(
      EtlIngestExecutionContext execCtx,
      Path snapshotFile,
      Class<?> elementType
  ) {

    public String key() {
      EtlSourceExecution ex = execCtx.execution();
      return ex.requestId() + ':' + ex.sourceId() + ':' + snapshotFile;
    }
  }

  /**
   * Row-level element (one parsed object from snapshot).
   */
  public record SnapshotRow<T>(SnapshotIngestContext context, T row, boolean last) {

  }

  /**
   * Batch for JDBC insert.
   */
  public record RawInsertBatch<T>(SnapshotIngestContext context, List<T> rows, boolean lastBatch) {

  }
}
