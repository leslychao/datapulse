package io.datapulse.etl.flow;

import static io.datapulse.domain.MessageCodes.DOWNLOAD_FAILED;
import static io.datapulse.domain.MessageCodes.ETL_CONTEXT_MISSING;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ERRORS;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_SAVE_BATCH;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_SNAPSHOT_READY;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_REQUEST_ID;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SNAPSHOT_FILE;
import static io.datapulse.etl.flow.EtlFlowConstants.HDR_ETL_SNAPSHOT_ID;
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
import io.datapulse.etl.flow.dto.EtlSourceExecution;
import io.datapulse.etl.flow.dto.IngestResult;
import io.datapulse.etl.i18n.ExceptionMessageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.BaseIntegrationFlowDefinition;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.handler.advice.ErrorMessageSendingRecoverer;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
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
  private final ExceptionMessageService exceptionMessageService;

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

  @Bean(name = CH_ETL_ERRORS)
  public MessageChannel etlIngestErrorChannel() {
    return new DirectChannel();
  }

  @Bean
  public Advice ingestResultAdvice() {
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
          MessageHeaders headers = message.getHeaders();
          String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);

          log.warn(
              "ETL ingest fetchSnapshot failed: sourceId={}, exceptionClass={}, message={}",
              sourceId,
              ex.getClass().getName(),
              ex.getMessage()
          );

          return new IngestResult(
              sourceId,
              false,
              ex.getClass().getSimpleName(),
              ex.getMessage()
          );
        }
      }
    };
  }

  @Bean
  public Advice snapshotErrorAdvice(
      @Qualifier(CH_ETL_ERRORS) MessageChannel etlIngestErrorChannel
  ) {
    ErrorMessageSendingRecoverer recoverer =
        new ErrorMessageSendingRecoverer(etlIngestErrorChannel);

    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));

    RequestHandlerRetryAdvice advice = new RequestHandlerRetryAdvice();
    advice.setRetryTemplate(retryTemplate);
    advice.setRecoveryCallback(recoverer);
    return advice;
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
            payload -> payload instanceof Snapshot<?>,
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
  public IntegrationFlow snapshotStreamingFlow(
      TaskExecutor etlIngestExecutor
  ) {
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
  public IntegrationFlow snapshotPersistFlow(Advice snapshotErrorAdvice) {
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
                .advice(snapshotErrorAdvice)
        )
        .get();
  }

  @Bean
  public IntegrationFlow snapshotErrorHandlingFlow() {
    return IntegrationFlow
        .from(CH_ETL_ERRORS)
        .handle(this::handleSnapshotIngestionError,
            endpoint -> endpoint.requiresReply(false))
        .get();
  }

  private Object fetchSnapshotOrThrow(
      EtlSourceExecution command,
      MessageHeaders headers
  ) {
    EtlContext context = requireEtlContext(headers, false);

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

    EtlContext context = requireEtlContext(headers, false);

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
      deleteSnapshotFileSafely(snapshot.file(), "missing-snapshot-id");
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
    EtlContext context = requireEtlContext(headers, true);

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

  private void handleSnapshotIngestionError(Message<?> errorMessage) {
    Object errorPayload = errorMessage.getPayload();
    MessageHeaders headers = errorMessage.getHeaders();
    MessageHeaders failedHeaders;
    Throwable cause;

    if (errorPayload instanceof MessagingException exception) {
      Message<?> failedMessage = exception.getFailedMessage();
      failedHeaders = failedMessage != null ? failedMessage.getHeaders() : headers;
      cause = exception.getCause() != null ? exception.getCause() : exception;
    } else if (errorPayload instanceof Throwable throwable) {
      failedHeaders = headers;
      cause = throwable;
    } else {
      log.warn(
          "Snapshot ingestion error received with unexpected payload type: {}; headers={}",
          errorPayload.getClass().getName(),
          headers
      );
      return;
    }

    String snapshotId = failedHeaders.get(HDR_ETL_SNAPSHOT_ID, String.class);
    Path snapshotFile = failedHeaders.get(HDR_ETL_SNAPSHOT_FILE, Path.class);

    if (snapshotId != null) {
      snapshotCommitBarrier.discard(snapshotId);
    } else if (snapshotFile != null) {
      deleteSnapshotFileSafely(snapshotFile, "error-without-snapshot-id");
    } else {
      log.warn(
          "Snapshot ingestion error received without snapshot context; headers={}",
          failedHeaders
      );
    }

    exceptionMessageService.logEtlError(cause);
  }

  private void deleteSnapshotFileSafely(Path file, String reason) {
    if (file == null) {
      log.warn("deleteSnapshotFileSafely(): null file, reason={}", reason);
      return;
    }
    try {
      boolean deleted = Files.deleteIfExists(file);
      log.debug(
          "Snapshot file delete (direct): file={}, deleted={}, reason={}",
          file,
          deleted,
          reason
      );
    } catch (IOException ex) {
      log.warn(
          "Snapshot file delete (direct) failed: file={}, reason={}",
          file,
          reason,
          ex
      );
    }
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

  private EtlContext requireEtlContext(
      MessageHeaders headers,
      boolean snapshotRequired
  ) {
    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String event = headers.get(HDR_ETL_EVENT, String.class);
    MarketplaceType marketplace =
        headers.get(HDR_ETL_SOURCE_MP, MarketplaceType.class);
    String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);
    String snapshotId = headers.get(HDR_ETL_SNAPSHOT_ID, String.class);
    Path snapshotFile = headers.get(HDR_ETL_SNAPSHOT_FILE, Path.class);

    List<String> missing = new ArrayList<>();

    addIfMissing(missing, requestId, "requestId");
    addIfMissing(missing, accountId, "accountId");
    addIfMissing(missing, event, "event");
    addIfMissing(missing, marketplace, "marketplace");
    addIfMissing(missing, sourceId, "sourceId");

    if (snapshotRequired) {
      addIfMissing(missing, snapshotId, "snapshotId");
      addIfMissing(missing, snapshotFile, "snapshotFile");
    }

    if (!missing.isEmpty()) {
      throw new AppException(ETL_CONTEXT_MISSING, String.join(", ", missing));
    }

    return new EtlContext(
        requestId,
        accountId,
        event,
        marketplace,
        sourceId,
        snapshotId,
        snapshotFile
    );
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

  private record EtlContext(
      String requestId,
      Long accountId,
      String event,
      MarketplaceType marketplace,
      String sourceId,
      String snapshotId,
      Path snapshotFile
  ) {

  }
}
