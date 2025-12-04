package io.datapulse.etl.flow.snapshot;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.flow.core.model.ExecutionDescriptor;
import io.datapulse.etl.flow.core.model.ExecutionOutcome;
import io.datapulse.etl.flow.core.model.ExecutionStatus;
import io.datapulse.etl.file.SnapshotCommitBarrier;
import io.datapulse.etl.file.SnapshotFileCleaner;
import io.datapulse.etl.file.SnapshotIteratorFactory;
import io.datapulse.etl.file.locator.JsonArrayLocator;
import io.datapulse.etl.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.etl.handler.error.EtlIngestErrorHandler;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.Snapshot;
import java.util.ArrayList;
import java.util.List;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.stereotype.Component;
import org.springframework.integration.support.MessageBuilder;

@Component
public class SnapshotIngestionPipeline {

  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final SnapshotFileCleaner snapshotFileCleaner;
  private final RawBatchInsertJdbcRepository rawBatchInsertJdbcRepository;
  private final EtlIngestErrorHandler ingestErrorHandler;

  public SnapshotIngestionPipeline(
      SnapshotCommitBarrier snapshotCommitBarrier,
      SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry,
      SnapshotIteratorFactory snapshotIteratorFactory,
      SnapshotFileCleaner snapshotFileCleaner,
      RawBatchInsertJdbcRepository rawBatchInsertJdbcRepository,
      EtlIngestErrorHandler ingestErrorHandler
  ) {
    this.snapshotCommitBarrier = snapshotCommitBarrier;
    this.snapshotJsonLayoutRegistry = snapshotJsonLayoutRegistry;
    this.snapshotIteratorFactory = snapshotIteratorFactory;
    this.snapshotFileCleaner = snapshotFileCleaner;
    this.rawBatchInsertJdbcRepository = rawBatchInsertJdbcRepository;
    this.ingestErrorHandler = ingestErrorHandler;
  }

  public ExecutionOutcome ingest(ExecutionDescriptor descriptor) {
    try {
      Snapshot<?> snapshot = descriptor.source().fetchSnapshot(
          descriptor.accountId(),
          descriptor.event(),
          descriptor.from(),
          descriptor.to()
      );

      if (snapshot == null) {
        throw new AppException(MessageCodes.DOWNLOAD_FAILED, "snapshot is null");
      }

      if (snapshot.sizeBytes() == 0L) {
        return new ExecutionOutcome(descriptor, ExecutionStatus.NO_DATA, null, null, null);
      }

      String snapshotId = registerSnapshot(descriptor, snapshot);
      int persisted = streamSnapshot(descriptor, snapshot, snapshotId);

      if (persisted == 0) {
        snapshotCommitBarrier.discard(snapshotId);
        snapshotFileCleaner.deleteSafely(snapshot.file(), "no-data");
        return new ExecutionOutcome(descriptor, ExecutionStatus.NO_DATA, null, null, snapshotId);
      }

      snapshotCommitBarrier.snapshotCompleted(snapshotId);
      snapshotFileCleaner.deleteSafely(snapshot.file(), "completed");
      return new ExecutionOutcome(descriptor, ExecutionStatus.SUCCESS, null, null, snapshotId);
    } catch (Exception ex) {
      var ingestResult = ingestErrorHandler.handleIngestError(
          ex,
          MessageBuilder
              .withPayload(descriptor)
              .setHeader(io.datapulse.etl.flow.core.FlowHeaders.HDR_SOURCE_ID, descriptor.sourceId())
              .build()
      );
      ExecutionStatus status = ingestResult.isWait() ? ExecutionStatus.WAITING : ExecutionStatus.ERROR;
      return new ExecutionOutcome(
          descriptor,
          status,
          ingestResult.errorMessage(),
          ingestResult.retryAfterSeconds(),
          null
      );
    }
  }

  private String registerSnapshot(ExecutionDescriptor descriptor, Snapshot<?> snapshot) {
    return snapshotCommitBarrier.registerSnapshot(
        snapshot.file(),
        descriptor.requestId(),
        descriptor.accountId(),
        descriptor.event(),
        descriptor.marketplace(),
        descriptor.sourceId()
    );
  }

  private int streamSnapshot(
      ExecutionDescriptor descriptor,
      Snapshot<?> snapshot,
      String snapshotId
  ) {
    Class<?> rawElementType = snapshot.elementType();
    JsonArrayLocator jsonArrayLocator = snapshotJsonLayoutRegistry.resolve(rawElementType);
    if (jsonArrayLocator == null) {
      snapshotCommitBarrier.discard(snapshotId);
      throw new AppException(
          MessageCodes.DOWNLOAD_FAILED,
          "JSON layout not found for type: " + rawElementType.getName()
      );
    }

    int persisted = 0;
    List<Object> batch = new ArrayList<>(SNAPSHOT_BATCH_SIZE);
    try (CloseableIterator<?> iterator = snapshotIteratorFactory.createIterator(
        snapshot.file(),
        rawElementType,
        snapshotId,
        jsonArrayLocator,
        snapshotCommitBarrier
    )) {
      while (iterator.hasNext()) {
        Object element = iterator.next();
        batch.add(element);
        if (batch.size() >= SNAPSHOT_BATCH_SIZE) {
          persistBatch(descriptor, snapshotId, batch);
          persisted += batch.size();
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        persistBatch(descriptor, snapshotId, batch);
        persisted += batch.size();
      }
    }
    return persisted;
  }

  private void persistBatch(
      ExecutionDescriptor descriptor,
      String snapshotId,
      List<Object> batch
  ) {
    snapshotCommitBarrier.registerBatch(snapshotId);
    rawBatchInsertJdbcRepository.saveBatch(
        batch,
        descriptor.rawTable(),
        descriptor.requestId(),
        snapshotId,
        descriptor.accountId(),
        descriptor.marketplace()
    );
    snapshotCommitBarrier.batchCompleted(snapshotId);
  }
}
