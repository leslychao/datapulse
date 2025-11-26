package io.datapulse.etl.flow;

import static io.datapulse.domain.MessageCodes.DOWNLOAD_FAILED;
import static io.datapulse.domain.MessageCodes.ETL_CONTEXT_MISSING;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_SNAPSHOT_READY;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SNAPSHOT_FILE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SNAPSHOT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SOURCE_ID;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.EtlSnapshotContext;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.IngestResult;
import io.datapulse.etl.file.SnapshotCommitBarrier;
import io.datapulse.etl.file.SnapshotFileCleaner;
import io.datapulse.etl.file.SnapshotIteratorFactory;
import io.datapulse.etl.file.locator.JsonArrayLocator;
import io.datapulse.etl.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.etl.handler.EtlBatchDispatcher;
import io.datapulse.etl.handler.error.EtlIngestErrorHandler;
import io.datapulse.etl.handler.error.EtlSnapshotErrorHandler;
import io.datapulse.marketplaces.dto.Snapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.BaseIntegrationFlowDefinition;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlSnapshotIngestionFlowConfig {

  private static final int SNAPSHOT_BATCH_SIZE = 500;
  private static final String HDR_ETL_SNAPSHOT_ORIGINAL = "ETL_SNAPSHOT_ORIGINAL";

  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final EtlBatchDispatcher etlBatchDispatcher;
  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final SnapshotFileCleaner snapshotFileCleaner;
  private final EtlSnapshotContextExtractor snapshotContextExtractor;

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
          return message.getHeaders().get(HDR_ETL_SNAPSHOT_ORIGINAL, Snapshot.class);
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
            (command, headers) -> fetchSnapshotOrThrow(
                command,
                new MessageHeaders(headers)
            ),
            e -> e.requiresReply(true).advice(ingestResultAdvice)
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
                    .gateway(
                        CH_ETL_SNAPSHOT_READY,
                        gatewaySpec -> gatewaySpec
                            .requestTimeout(0L)
                            .replyTimeout(-1L)
                            .requiresReply(true)
                    )
                    .handle(
                        Object.class,
                        (ignored, headers) -> new IngestResult(
                            headers.get(HDR_ETL_SOURCE_ID, String.class),
                            true,
                            null,
                            null
                        )
                    )
                )
                .subFlowMapping(false, subFlow -> subFlow
                    .handle(
                        Object.class,
                        (payload, headers) -> new IngestResult(
                            headers.get(HDR_ETL_SOURCE_ID, String.class),
                            false,
                            AppException.class.getName(),
                            "Snapshot is missing or has no file"
                        )
                    )
                )
        )
        .get();
  }

  @Bean
  public IntegrationFlow snapshotStreamingFlow(Advice snapshotPersistErrorAdvice) {
    return IntegrationFlow
        .from(CH_ETL_SNAPSHOT_READY)
        .enrichHeaders(enricher -> enricher
            .headerFunction(HDR_ETL_SNAPSHOT_ID, this::registerSnapshotInBarrier)
            .headerFunction(
                HDR_ETL_SNAPSHOT_ORIGINAL,
                message -> requireSnapshotPayload(message.getPayload())
            )
        )
        .split(Message.class, this::toSnapshotIterator)
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message ->
                message.getHeaders().get(HDR_ETL_SNAPSHOT_ID, String.class)
            )
            .releaseStrategy(group -> group.size() >= SNAPSHOT_BATCH_SIZE)
            .groupTimeout(1_000)
            .expireGroupsUponCompletion(true)
            .sendPartialResultOnExpiry(true)
        )
        .<List<?>>handle(
            (rawBatch, headersMap) -> {
              MessageHeaders headers = new MessageHeaders(headersMap);
              persistBatch(rawBatch, headers);
              return headers.get(HDR_ETL_SNAPSHOT_ORIGINAL, Snapshot.class);
            },
            endpoint -> endpoint
                .requiresReply(true)
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
