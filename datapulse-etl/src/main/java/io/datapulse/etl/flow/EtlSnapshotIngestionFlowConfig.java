package io.datapulse.etl.flow;

import static io.datapulse.domain.MessageCodes.DOWNLOAD_FAILED;
import static io.datapulse.domain.MessageCodes.ETL_CONTEXT_MISSING;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_SAVE_BATCH;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_SNAPSHOT_READY;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SNAPSHOT_FILE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SNAPSHOT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.marketplace.Snapshot;
import io.datapulse.etl.file.SnapshotCommitBarrier;
import io.datapulse.etl.file.SnapshotFileCleaner;
import io.datapulse.etl.file.SnapshotIteratorFactory;
import io.datapulse.etl.file.locator.JsonArrayLocator;
import io.datapulse.etl.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.etl.batch.EtlBatchDispatcher;
import io.datapulse.etl.flow.dto.EtlSnapshotContext;
import io.datapulse.etl.flow.dto.EtlSourceExecution;
import io.datapulse.etl.flow.dto.IngestResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.dsl.BaseIntegrationFlowDefinition;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlSnapshotIngestionFlowConfig {

  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final EtlBatchDispatcher etlBatchDispatcher;
  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final SnapshotFileCleaner snapshotFileCleaner;
  private final EtlSnapshotContextExtractor snapshotContextExtractor;

  @Bean(name = "etlIngestExecutor")
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

  @Bean
  public Advice ingestResultAdvice(EtlIngestErrorHandler ingestErrorHandler) {
    return new AbstractRequestHandlerAdvice() {
      @Override
      protected Object doInvoke(
          ExecutionCallback callback,
          Object target,
          Message<?> message
      ) {
        try {
          return callback.execute();
        } catch (Exception ex) {
          Throwable actual = unwrapThrowable(ex);
          return ingestErrorHandler.handleIngestError(actual, message);
        }
      }

      private Throwable unwrapThrowable(Throwable throwable) {
        if (throwable instanceof ThrowableHolderException holder
            && holder.getCause() != null) {
          return holder.getCause();
        }
        return throwable;
      }
    };
  }

  @Bean
  public Advice snapshotPersistErrorAdvice(EtlSnapshotErrorHandler snapshotErrorHandler) {
    return new AbstractRequestHandlerAdvice() {
      @Override
      protected Object doInvoke(
          ExecutionCallback callback,
          Object target,
          Message<?> message
      ) {
        try {
          return callback.execute();
        } catch (Exception ex) {
          snapshotErrorHandler.handlePersistError(ex, message);
          return null;
        }
      }
    };
  }

  @Bean
  public IntegrationFlow etlIngestFlow(Advice ingestResultAdvice) {
    return IntegrationFlow
        .from(CH_ETL_INGEST)
        .handle(
            EtlSourceExecution.class,
            (command, headersMap) -> fetchSnapshotOrThrow(
                command,
                new MessageHeaders(headersMap)
            ),
            endpoint -> endpoint
                .requiresReply(true)
                .advice(ingestResultAdvice)
        )
        .<Object, Boolean>route(
            payload -> payload instanceof Snapshot<?> snapshot && snapshot.file() != null,
            mapping -> mapping
                .subFlowMapping(true, subFlow -> subFlow
                    .enrichHeaders(enricher -> enricher
                        .headerFunction(
                            HDR_ETL_SNAPSHOT_FILE,
                            message -> requireSnapshotPayload(message.getPayload()).file()
                        )
                    )
                    .wireTap(CH_ETL_SNAPSHOT_READY)
                    .handle(
                        Snapshot.class,
                        (snapshot, headers) -> {
                          String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);
                          return new IngestResult(
                              sourceId,
                              true,
                              null,
                              null
                          );
                        }
                    )
                )
                .subFlowMapping(false, BaseIntegrationFlowDefinition::bridge)
        )
        .get();
  }

  @Bean
  public IntegrationFlow snapshotStreamingFlow(TaskExecutor etlIngestExecutor) {
    return IntegrationFlow
        .from(CH_ETL_SNAPSHOT_READY)
        .enrichHeaders(enricher -> enricher
            .headerFunction(HDR_ETL_SNAPSHOT_ID, this::registerSnapshotInBarrier)
        )
        .split(Message.class, this::toSnapshotIterator)
        .channel(c -> c.executor(etlIngestExecutor))
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message -> {
              String snapshotId =
                  message.getHeaders().get(HDR_ETL_SNAPSHOT_ID, String.class);
              if (snapshotId == null) {
                throw new AppException(ETL_CONTEXT_MISSING, "snapshotId");
              }
              return snapshotId;
            })
            .releaseStrategy(group -> group.size() >= SNAPSHOT_BATCH_SIZE)
            .groupTimeout(1_000)
            .expireGroupsUponCompletion(true)
            .sendPartialResultOnExpiry(true)
        )
        .channel(CH_ETL_SAVE_BATCH)
        .get();
  }

  @Bean
  public IntegrationFlow snapshotPersistFlow(Advice snapshotPersistErrorAdvice) {
    return IntegrationFlow
        .from(CH_ETL_SAVE_BATCH)
        .<List<?>>handle(
            (rawBatch, headersMap) -> {
              MessageHeaders headers = new MessageHeaders(headersMap);
              persistBatch(rawBatch, headers);
              return null;
            },
            endpoint -> endpoint
                .requiresReply(false)
                .advice(snapshotPersistErrorAdvice)
        )
        .get();
  }

  private Object fetchSnapshotOrThrow(
      EtlSourceExecution command,
      MessageHeaders headers
  ) {
    EtlSnapshotContext context = requireEtlContext(headers, false);

    MarketplaceEvent event = MarketplaceEvent.fromString(context.event());

    Object rawSnapshot = command.source().fetchSnapshot(
        command.accountId(),
        command.event(),
        command.from(),
        command.to()
    );

    log.info(
        "ETL snapshot fetched: requestId={}, accountId={}, event={}, marketplace={}, sourceId={}",
        context.requestId(),
        context.accountId(),
        event,
        context.marketplace(),
        context.sourceId()
    );
    return rawSnapshot;
  }

  private String registerSnapshotInBarrier(Message<?> message) {
    Snapshot<?> snapshot = requireSnapshotPayload(message.getPayload());
    MessageHeaders headers = message.getHeaders();

    EtlSnapshotContext context = requireEtlContext(headers, false);

    MarketplaceEvent event = MarketplaceEvent.fromString(context.event());

    Path snapshotFile = snapshot.file();
    String snapshotId = snapshotCommitBarrier.registerSnapshot(
        snapshotFile,
        context.requestId(),
        context.accountId(),
        event,
        context.marketplace(),
        context.sourceId()
    );

    log.info(
        "ETL snapshot registered in barrier: requestId={}, snapshotId={}, event={}, marketplace={}, sourceId={}",
        context.requestId(),
        snapshotId,
        event,
        context.marketplace(),
        context.sourceId()
    );

    return snapshotId;
  }

  private CloseableIterator<?> toSnapshotIterator(Message<?> message) {
    Snapshot<?> snapshot = requireSnapshotPayload(message.getPayload());
    MessageHeaders headers = message.getHeaders();

    String snapshotId = headers.get(HDR_ETL_SNAPSHOT_ID, String.class);
    if (snapshotId == null) {
      snapshotFileCleaner.deleteSafely(snapshot.file(), "missing-snapshot-id");
      throw new AppException(ETL_CONTEXT_MISSING, "snapshotId");
    }

    Class<?> rawElementType = snapshot.elementType();
    JsonArrayLocator jsonArrayLocator = snapshotJsonLayoutRegistry.resolve(rawElementType);
    if (jsonArrayLocator == null) {
      snapshotCommitBarrier.discard(snapshotId);
      throw new AppException(
          DOWNLOAD_FAILED,
          "JSON layout not found for type: " + rawElementType.getName()
      );
    }

    return snapshotIteratorFactory.createIterator(
        snapshot.file(),
        rawElementType,
        snapshotId,
        jsonArrayLocator,
        snapshotCommitBarrier
    );
  }

  private void persistBatch(
      List<?> rawBatch,
      MessageHeaders headers
  ) {
    EtlSnapshotContext context = requireEtlContext(headers, true);

    snapshotCommitBarrier.registerBatch(context.snapshotId());

    etlBatchDispatcher.dispatch(
        rawBatch,
        context.requestId(),
        context.snapshotId(),
        context.accountId(),
        context.marketplace()
    );
    snapshotCommitBarrier.batchCompleted(context.snapshotId());

    log.debug(
        "ETL snapshot batch persisted: requestId={}, snapshotId={}, sourceId={}, batchSize={}",
        context.requestId(),
        context.snapshotId(),
        context.sourceId(),
        rawBatch.size()
    );
  }

  private <T> void addIfMissing(
      List<String> missing,
      T value,
      String name
  ) {
    if (value == null) {
      missing.add(name);
    }
  }

  private EtlSnapshotContext requireEtlContext(
      MessageHeaders headers,
      boolean snapshotRequired
  ) {
    EtlSnapshotContext context = snapshotContextExtractor.extract(headers);

    List<String> missing = new ArrayList<>();

    addIfMissing(missing, context.requestId(), "requestId");
    addIfMissing(missing, context.accountId(), "accountId");
    addIfMissing(missing, context.event(), "event");
    addIfMissing(missing, context.marketplace(), "marketplace");
    addIfMissing(missing, context.sourceId(), "sourceId");

    if (snapshotRequired) {
      addIfMissing(missing, context.snapshotId(), "snapshotId");
      addIfMissing(missing, context.snapshotFile(), "snapshotFile");
    }

    if (!missing.isEmpty()) {
      throw new AppException(ETL_CONTEXT_MISSING, String.join(", ", missing));
    }

    return context;
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
