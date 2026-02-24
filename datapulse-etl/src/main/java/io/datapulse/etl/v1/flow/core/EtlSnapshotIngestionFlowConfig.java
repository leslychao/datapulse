package io.datapulse.etl.v1.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_INGEST_JSON_LAYOUT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_ELEMENT_TYPE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_FILE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_REQUIRED;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import io.datapulse.etl.v1.file.IngestItemIterator;
import io.datapulse.etl.v1.file.SnapshotIteratorFactory;
import io.datapulse.etl.v1.file.locator.JsonArrayLocator;
import io.datapulse.etl.v1.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.marketplaces.dto.Snapshot;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.splitter.DefaultMessageSplitter;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.util.CollectionUtils;

@Configuration
@RequiredArgsConstructor
public class EtlSnapshotIngestionFlowConfig {

  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final RawBatchInsertJdbcRepository repository;

  @Bean
  public IntegrationFlow etlIngestFlow() {
    return IntegrationFlow
        .from(EtlIngestGateway.class)
        .transform(IngestCommand.class, this::fetchSnapshotsAndBuildContexts)
        .split(new DefaultMessageSplitter())
        .split(IngestContext.class, this::toIngestItemIterator)
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message -> ((IngestItem<?>) message.getPayload()).context().key())
            .releaseStrategy(group -> group.size() >= SNAPSHOT_BATCH_SIZE
                || group.getMessages().stream()
                .map(m -> (IngestItem<?>) m.getPayload())
                .anyMatch(IngestItem::last))
            .outputProcessor(group -> {
              IngestItem<?> sample = (IngestItem<?>) group.getOne().getPayload();
              List<?> rows = group.getMessages().stream().map(m -> ((IngestItem<?>) m.getPayload()).row()).toList();
              return new IngestBatch<>(sample.context(), rows);
            })
            .expireGroupsUponCompletion(true))
        .handle(IngestBatch.class, (batch, headers) -> {
          saveBatch(batch);
          return null;
        }, endpoint -> endpoint.requiresReply(false))
        .get();
  }

  private List<IngestContext> fetchSnapshotsAndBuildContexts(IngestCommand command) {
    List<Snapshot<?>> snapshots = command.registeredSource().source().fetchSnapshots(
        command.execution().accountId(),
        command.execution().event(),
        command.execution().dateFrom(),
        command.execution().dateTo());

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
      return new IngestContext(command.execution(), command.registeredSource(), file, elementType);
    }).toList();
  }

  private CloseableIterator<IngestItem<?>> toIngestItemIterator(IngestContext ingestContext) {
    JsonArrayLocator locator = snapshotJsonLayoutRegistry.resolve(ingestContext.elementType());
    if (locator == null) {
      throw new AppException(ETL_INGEST_JSON_LAYOUT_NOT_FOUND, ingestContext.elementType());
    }
    CloseableIterator<?> delegate = snapshotIteratorFactory.createIterator(
        ingestContext.snapshotFile(), ingestContext.elementType(), locator);
    return new IngestItemIterator(delegate, ingestContext);
  }

  private void saveBatch(IngestBatch<?> batch) {
    if (batch.rows().isEmpty()) {
      return;
    }
    EtlSourceExecution execution = batch.context().execution();
    RegisteredSource source = batch.context().registeredSource();
    repository.saveBatch(batch.rows(), source.rawTable(), execution.requestId(), execution.accountId(), source.marketplace());
  }

  public record IngestCommand(EtlSourceExecution execution, RegisteredSource registeredSource) {}

  public interface EtlIngestGateway {
    void ingest(IngestCommand command);
  }

  public record IngestContext(EtlSourceExecution execution, RegisteredSource registeredSource,
                              Path snapshotFile, Class<?> elementType) {
    public String key() {
      return execution.requestId() + ':' + execution.sourceId() + ':' + snapshotFile;
    }
  }

  public record IngestItem<T>(IngestContext context, T row, boolean last) {}

  public record IngestBatch<T>(IngestContext context, List<T> rows) {}
}
