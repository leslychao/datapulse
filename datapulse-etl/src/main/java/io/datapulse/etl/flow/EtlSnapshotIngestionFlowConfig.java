package io.datapulse.etl.flow;

import static io.datapulse.domain.MessageCodes.DOWNLOAD_FAILED;
import static io.datapulse.domain.MessageCodes.ETL_CONTEXT_MISSING;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_MP;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.marketplace.Snapshot;
import io.datapulse.etl.file.SnapshotCommitBarrier;
import io.datapulse.etl.file.SnapshotIteratorFactory;
import io.datapulse.etl.file.locator.JsonArrayLocator;
import io.datapulse.etl.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.etl.flow.batch.EtlBatchDispatcher;
import io.datapulse.etl.flow.dto.EtlIngestResult;
import io.datapulse.etl.flow.dto.EtlSourceExecution;
import io.datapulse.etl.i18n.ExceptionMessageService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
public class EtlSnapshotIngestionFlowConfig {

  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final EtlBatchDispatcher etlBatchDispatcher;
  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final ExceptionMessageService exceptionMessageService;

  public EtlSnapshotIngestionFlowConfig(
      SnapshotCommitBarrier snapshotCommitBarrier,
      EtlBatchDispatcher etlBatchDispatcher,
      SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry,
      SnapshotIteratorFactory snapshotIteratorFactory,
      ExceptionMessageService exceptionMessageService
  ) {
    this.snapshotCommitBarrier = snapshotCommitBarrier;
    this.etlBatchDispatcher = etlBatchDispatcher;
    this.snapshotJsonLayoutRegistry = snapshotJsonLayoutRegistry;
    this.snapshotIteratorFactory = snapshotIteratorFactory;
    this.exceptionMessageService = exceptionMessageService;
  }

  @Bean("etlIngestExecutor")
  public TaskExecutor etlIngestExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    int cores = Runtime.getRuntime().availableProcessors();
    int corePool = Math.max(1, cores - 1);
    executor.setCorePoolSize(corePool);
    executor.setMaxPoolSize(corePool * 2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("etl-ingest-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  EtlIngestResult handleIngest(
      EtlSourceExecution command,
      MessageHeaders headers
  ) {
    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = headers.get(HDR_ETL_EVENT, String.class);
    MarketplaceType marketplace =
        headers.get(HDR_ETL_SOURCE_MP, MarketplaceType.class);
    String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);

    validateRequiredContext(requestId, accountId, eventValue, marketplace, sourceId);

    MarketplaceEvent event = MarketplaceEvent.fromString(eventValue);

    Object rawSnapshot;
    try {
      rawSnapshot = command.source().fetchSnapshot(
          command.accountId(),
          command.event(),
          command.from(),
          command.to()
      );
    } catch (Throwable throwable) {
      log.warn(
          "ETL source failed while fetching snapshot: requestId={}, accountId={}, "
              + "event={}, marketplace={}, sourceId={}",
          requestId,
          accountId,
          event,
          marketplace,
          sourceId,
          throwable
      );
      throw throwable;
    }

    Snapshot<?> snapshot = requireSnapshotPayload(rawSnapshot);
    Path snapshotFile = snapshot.file();

    String snapshotId = snapshotCommitBarrier.registerSnapshot(
        snapshotFile,
        requestId,
        accountId,
        event,
        marketplace,
        sourceId
    );

    Class<?> rawElementType = snapshot.elementType();
    JsonArrayLocator jsonArrayLocator = snapshotJsonLayoutRegistry.resolve(rawElementType);
    if (jsonArrayLocator == null) {
      snapshotCommitBarrier.discard(snapshotId, snapshotFile);
      throw new AppException(
          DOWNLOAD_FAILED,
          "JSON layout not found for type: " + rawElementType.getName()
      );
    }

    long totalElements = 0L;
    List<Object> batch = new ArrayList<>(SNAPSHOT_BATCH_SIZE);

    try (CloseableIterator<?> iterator = snapshotIteratorFactory.createIterator(
        snapshotFile,
        rawElementType,
        snapshotId,
        jsonArrayLocator,
        snapshotCommitBarrier
    )) {

      while (iterator.hasNext()) {
        Object element = iterator.next();
        batch.add(element);
        totalElements++;

        if (batch.size() == SNAPSHOT_BATCH_SIZE) {
          persistBatch(batch, requestId, snapshotId, accountId, marketplace);
          batch.clear();
        }
      }

      if (!batch.isEmpty()) {
        persistBatch(batch, requestId, snapshotId, accountId, marketplace);
      }

      log.info(
          "ETL snapshot ingested successfully: requestId={}, snapshotId={}, "
              + "event={}, marketplace={}, sourceId={}, totalElements={}",
          requestId,
          snapshotId,
          event,
          marketplace,
          sourceId,
          totalElements
      );

      return new EtlIngestResult(
          snapshotId,
          marketplace,
          sourceId,
          totalElements
      );

    } catch (Throwable throwable) {
      snapshotCommitBarrier.discard(snapshotId, snapshotFile);
      exceptionMessageService.logEtlError(throwable);

      log.warn(
          "ETL snapshot ingestion failed; data for this source will be discarded. "
              + "requestId={}, event={}, marketplace={}, sourceId={}, snapshotId={}, accountId={}",
          requestId,
          event,
          marketplace,
          sourceId,
          snapshotId,
          accountId,
          throwable
      );

      throw throwable;
    }
  }

  private void persistBatch(
      List<?> rawBatch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    snapshotCommitBarrier.registerBatch(snapshotId);
    try {
      etlBatchDispatcher.dispatch(
          rawBatch,
          requestId,
          snapshotId,
          accountId,
          marketplace
      );
      snapshotCommitBarrier.batchCompleted(snapshotId);
      log.debug(
          "ETL snapshot batch persisted: requestId={}, snapshotId={}, batchSize={}",
          requestId,
          snapshotId,
          rawBatch.size()
      );
    } catch (Throwable throwable) {
      snapshotCommitBarrier.batchCompleted(snapshotId);
      throw throwable;
    }
  }

  private void validateRequiredContext(
      String requestId,
      Long accountId,
      String event,
      MarketplaceType marketplace,
      String sourceId
  ) {
    List<String> missing = new ArrayList<>();

    if (requestId == null) {
      missing.add("requestId");
    }
    if (accountId == null) {
      missing.add("accountId");
    }
    if (event == null) {
      missing.add("event");
    }
    if (marketplace == null) {
      missing.add("marketplace");
    }
    if (sourceId == null) {
      missing.add("sourceId");
    }

    if (!missing.isEmpty()) {
      throw new AppException(ETL_CONTEXT_MISSING, String.join(", ", missing));
    }
  }

  private Snapshot<?> requireSnapshotPayload(Object payload) {
    if (!(payload instanceof Snapshot<?> snapshot)) {
      String payloadType = payload != null ? payload.getClass().getName() : "null";
      throw new AppException(DOWNLOAD_FAILED, payloadType);
    }
    if (snapshot.file() == null) {
      throw new AppException(DOWNLOAD_FAILED, "snapshot file is null");
    }
    if (snapshot.elementType() == null) {
      throw new AppException(DOWNLOAD_FAILED, "snapshot elementType is null");
    }
    return snapshot;
  }
}
