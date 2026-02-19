package io.datapulse.etl.v1.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_INGEST_JSON_LAYOUT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_ELEMENT_TYPE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_FILE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_REQUIRED;
import static io.datapulse.etl.v1.flow.core.EtlFlowConstants.CH_ETL_INGEST;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import io.datapulse.etl.v1.file.IngestItemIterator;
import io.datapulse.etl.v1.file.SnapshotIteratorFactory;
import io.datapulse.etl.v1.file.locator.JsonArrayLocator;
import io.datapulse.etl.v1.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.Snapshot;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.splitter.DefaultMessageSplitter;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.Message;
import org.springframework.util.CollectionUtils;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlSnapshotIngestionFlowConfig {

  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final RawBatchInsertJdbcRepository repository;

  @Bean
  public IntegrationFlow etlIngestFlow() {
    return IntegrationFlow
        .from(CH_ETL_INGEST)
        .handle(
            EtlSourceExecution.class,
            this::fetchSnapshotsAndBuildContexts
        )
        .log(message -> {
          List<?> contexts = (List<?>) message.getPayload();
          IngestContext context = (IngestContext) contexts.get(0);
          EtlSourceExecution execution = context.execution();

          return String.format(
              "ETL snapshots ready for ingest: requestId=%s, accountId=%s, event=%s, " +
                  "marketplace=%s, sourceId=%s, rawTable=%s, snapshots=%s, firstFile=%s",
              execution.requestId(),
              execution.accountId(),
              execution.event(),
              execution.marketplace(),
              execution.sourceId(),
              execution.rawTable(),
              contexts.size(),
              context.snapshotFile()
          );
        })
        .split(new DefaultMessageSplitter())
        .split(
            IngestContext.class,
            this::toIngestItemIterator
        )
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message -> {
              IngestItem<?> ingestItem = (IngestItem<?>) message.getPayload();
              EtlSourceExecution execution = ingestItem.context().execution();

              return String.format(
                  "%s:%s:%s:%s:%s",
                  execution.requestId(),
                  execution.accountId(),
                  execution.event(),
                  execution.marketplace(),
                  execution.sourceId()
              );
            })
            .releaseStrategy(group -> {
              if (group.size() >= SNAPSHOT_BATCH_SIZE) {
                return true;
              }

              return group.getMessages()
                  .stream()
                  .map(Message::getPayload)
                  .filter(IngestItem.class::isInstance)
                  .map(payload -> (IngestItem<?>) payload)
                  .anyMatch(IngestItem::last);
            })
            .sendPartialResultOnExpiry(true)
            .outputProcessor(group -> {
              Message<?> sampleMessage = group.getOne();
              IngestItem<?> sampleItem = (IngestItem<?>) sampleMessage.getPayload();

              IngestContext context = sampleItem.context();

              List<?> rows = group.getMessages()
                  .stream()
                  .map(message -> {
                    IngestItem<?> ingestItem = (IngestItem<?>) message.getPayload();
                    return ingestItem.row();
                  })
                  .toList();

              return new IngestBatch<>(context, rows);
            })
            .expireGroupsUponCompletion(true)
        )
        .handle(
            IngestBatch.class,
            this::handleBatch,
            endpoint -> endpoint.requiresReply(false)
        )
        .get();
  }

  private List<IngestContext> fetchSnapshotsAndBuildContexts(
      EtlSourceExecution execution,
      Map<String, Object> headersMap
  ) {
    List<Snapshot<?>> snapshots = execution
        .source()
        .fetchSnapshots(
            execution.accountId(),
            execution.event(),
            execution.dateFrom(),
            execution.dateTo()
        );

    if (CollectionUtils.isEmpty(snapshots)) {
      throw new AppException(ETL_INGEST_SNAPSHOT_REQUIRED);
    }

    return snapshots.stream()
        .map(snapshot -> {
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

          return new IngestContext(execution, file, elementType);
        })
        .toList();
  }

  private CloseableIterator<IngestItem<?>> toIngestItemIterator(IngestContext ingestContext) {
    JsonArrayLocator locator = snapshotJsonLayoutRegistry.resolve(ingestContext.elementType());
    if (locator == null) {
      throw new AppException(
          ETL_INGEST_JSON_LAYOUT_NOT_FOUND,
          ingestContext.elementType()
      );
    }

    CloseableIterator<?> delegate = snapshotIteratorFactory.createIterator(
        ingestContext.snapshotFile(),
        ingestContext.elementType(),
        locator
    );

    return new IngestItemIterator(delegate, ingestContext);
  }

  private Object handleBatch(
      IngestBatch<?> batch,
      Map<String, Object> headersMap
  ) {
    List<?> rows = batch.rows();
    if (rows.isEmpty()) {
      return null;
    }

    IngestContext context = batch.context();
    EtlSourceExecution execution = context.execution();
    String requestId = execution.requestId();

    repository.saveBatch(
        rows,
        execution.rawTable(),
        requestId,
        execution.accountId(),
        execution.marketplace()
    );

    log.debug(
        "ETL snapshot batch persisted: requestId={}, sourceId={}, batchSize={}",
        requestId,
        execution.sourceId(),
        rows.size()
    );

    return null;
  }

  public record IngestContext(
      EtlSourceExecution execution,
      Path snapshotFile,
      Class<?> elementType
  ) {

  }

  public record IngestItem<T>(
      IngestContext context,
      T row,
      boolean last
  ) {

  }

  public record IngestBatch<T>(
      IngestContext context,
      List<T> rows
  ) {

  }
}
