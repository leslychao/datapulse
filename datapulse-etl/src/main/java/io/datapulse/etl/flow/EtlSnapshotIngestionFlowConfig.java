package io.datapulse.etl.flow;

import static io.datapulse.domain.MessageCodes.DOWNLOAD_FAILED;
import static io.datapulse.domain.MessageCodes.ETL_CONTEXT_MISSING;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_ERRORS;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.EtlFlowConstants.CH_ETL_SAVE_BATCH;
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
import io.datapulse.etl.i18n.ExceptionMessageService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class EtlSnapshotIngestionFlowConfig {

  private static final String CH_ETL_SNAPSHOT_READY = "ETL_SNAPSHOT_READY";
  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final EtlBatchDispatcher etlBatchDispatcher;
  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final ExceptionMessageService exceptionMessageService;
  private final EtlOrchestratorFlowConfig orchestratorFlowConfig;

  @Bean("etlIngestExecutor")
  public TaskExecutor etlIngestExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    int cores = Runtime.getRuntime().availableProcessors();
    int corePool = Math.max(1, cores - 1);
    executor.setCorePoolSize(corePool);
    executor.setMaxPoolSize(corePool * 2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("etl-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = "errorChannel")
  public MessageChannel globalErrorChannel() {
    return new PublishSubscribeChannel();
  }

  @Bean
  public IntegrationFlow globalErrorBridgeFlow() {
    return IntegrationFlow
        .from("errorChannel")
        .channel(CH_ETL_ERRORS)
        .get();
  }

  @Bean(name = CH_ETL_INGEST)
  public MessageChannel etlIngestChannel(TaskExecutor etlIngestExecutor) {
    return new ExecutorChannel(etlIngestExecutor);
  }

  @Bean(name = CH_ETL_SNAPSHOT_READY)
  public MessageChannel snapshotReadyChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_ETL_SAVE_BATCH)
  public MessageChannel snapshotPersistBatchChannel() {
    return new DirectChannel();
  }

  @Bean(name = CH_ETL_ERRORS)
  public MessageChannel etlErrorChannel() {
    return new PublishSubscribeChannel();
  }

  @Bean
  public IntegrationFlow etlIngestFlow() {
    return IntegrationFlow
        .from(CH_ETL_INGEST)
        .handle(EtlSourceExecution.class, (command, headers) -> {
          try {
            Object rawSnapshot = command.source().fetchSnapshot(
                command.accountId(),
                command.event(),
                command.from(),
                command.to()
            );
            return requireSnapshotPayload(rawSnapshot);
          } catch (Throwable throwable) {
            log.warn(
                "ETL source failed before snapshot registration: requestId={}, accountId={}, "
                    + "event={}, marketplace={}, sourceId={}",
                headers.get(HDR_ETL_REQUEST_ID, String.class),
                headers.get(HDR_ETL_ACCOUNT_ID, Long.class),
                headers.get(HDR_ETL_EVENT, String.class),
                headers.get(HDR_ETL_SOURCE_MP, MarketplaceType.class),
                headers.get(HDR_ETL_SOURCE_ID, String.class),
                throwable
            );
            throw throwable;
          }
        })
        .enrichHeaders(enricher -> enricher
            .headerFunction(
                HDR_ETL_SNAPSHOT_FILE,
                message -> requireSnapshotPayload(message.getPayload()).file()
            )
        )
        .channel(CH_ETL_SNAPSHOT_READY)
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
            .groupTimeout(1000)
            .expireGroupsUponCompletion(true)
            .sendPartialResultOnExpiry(true)
        )
        .channel(CH_ETL_SAVE_BATCH)
        .get();
  }

  @Bean
  public IntegrationFlow snapshotPersistFlow() {
    return IntegrationFlow
        .from(CH_ETL_SAVE_BATCH)
        .<List<?>>handle((rawBatch, headers) -> {
          validateRequiredEtlHeaders(headers);

          String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
          String snapshotId = headers.get(HDR_ETL_SNAPSHOT_ID, String.class);
          Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
          MarketplaceType marketplace =
              headers.get(HDR_ETL_SOURCE_MP, MarketplaceType.class);
          Path snapshotFile = headers.get(HDR_ETL_SNAPSHOT_FILE, Path.class);
          String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);
          String event = headers.get(HDR_ETL_EVENT, String.class);

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
                "ETL snapshot batch persisted: requestId={}, snapshotId={}, sourceId={}, batchSize={}",
                requestId,
                snapshotId,
                sourceId,
                rawBatch.size()
            );
          } catch (Throwable throwable) {
            snapshotCommitBarrier.discard(snapshotId, snapshotFile);
            exceptionMessageService.logEtlError(throwable);

            log.warn(
                "ETL source execution failed; subsequent stages for this marketplace/event will NOT be started. "
                    + "requestId={}, event={}, marketplace={}, sourceId={}, snapshotId={}, accountId={}",
                requestId,
                event,
                marketplace,
                sourceId,
                snapshotId,
                accountId
            );
          }
          return null;
        }, endpoint -> endpoint.requiresReply(false))
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

  private String registerSnapshotInBarrier(Message<?> message) {
    Snapshot<?> snapshot = requireSnapshotPayload(message.getPayload());
    MessageHeaders headers = message.getHeaders();

    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    if (requestId == null) {
      throw new AppException(ETL_CONTEXT_MISSING, "requestId");
    }

    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    if (accountId == null) {
      throw new AppException(ETL_CONTEXT_MISSING, "accountId");
    }

    String eventValue = headers.get(HDR_ETL_EVENT, String.class);
    if (eventValue == null) {
      throw new AppException(ETL_CONTEXT_MISSING, "event");
    }

    MarketplaceEvent event = MarketplaceEvent.fromString(eventValue);
    if (event == null) {
      throw new AppException(ETL_CONTEXT_MISSING, "event=" + eventValue);
    }

    MarketplaceType marketplace = headers.get(HDR_ETL_SOURCE_MP, MarketplaceType.class);
    if (marketplace == null) {
      throw new AppException(ETL_CONTEXT_MISSING, "marketplace");
    }

    String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);
    if (sourceId == null) {
      throw new AppException(ETL_CONTEXT_MISSING, "sourceId");
    }

    Path snapshotFile = snapshot.file();

    return snapshotCommitBarrier.registerSnapshot(
        snapshotFile,
        requestId,
        accountId,
        event,
        marketplace,
        sourceId
    );
  }

  private CloseableIterator<?> toSnapshotIterator(Message<?> message) {
    Snapshot<?> snapshot = requireSnapshotPayload(message.getPayload());
    MessageHeaders headers = message.getHeaders();

    String snapshotId = headers.get(HDR_ETL_SNAPSHOT_ID, String.class);
    if (snapshotId == null) {
      throw new AppException(ETL_CONTEXT_MISSING, "snapshotId");
    }

    Class<?> rawElementType = snapshot.elementType();
    Path snapshotFile = snapshot.file();

    JsonArrayLocator jsonArrayLocator = snapshotJsonLayoutRegistry.resolve(rawElementType);
    if (jsonArrayLocator == null) {
      throw new AppException(
          DOWNLOAD_FAILED,
          "JSON layout not found for type: " + rawElementType.getName()
      );
    }

    return snapshotIteratorFactory.createIterator(
        snapshotFile,
        rawElementType,
        snapshotId,
        jsonArrayLocator,
        snapshotCommitBarrier
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

  private void handleSnapshotIngestionError(Message<?> message) {
    Message<?> failedMessage = message;
    Throwable rootCause = null;

    if (message.getPayload() instanceof MessagingException messagingException) {
      Message<?> innerFailed = messagingException.getFailedMessage();
      if (innerFailed != null) {
        failedMessage = innerFailed;
      }
      Throwable cause = messagingException.getCause();
      rootCause = (cause != null) ? cause : messagingException;
    } else if (message.getPayload() instanceof Throwable throwable) {
      rootCause = throwable;
    }

    MessageHeaders failedHeaders = failedMessage.getHeaders();

    String snapshotId = failedHeaders.get(HDR_ETL_SNAPSHOT_ID, String.class);
    Path snapshotFile = failedHeaders.get(HDR_ETL_SNAPSHOT_FILE, Path.class);

    if (snapshotId != null || snapshotFile != null) {
      snapshotCommitBarrier.discard(snapshotId, snapshotFile);
    } else {
      notifyOrchestratorAboutEarlySourceFailure(failedHeaders, rootCause);
    }

    String context = "snapshotId=%s, file=%s".formatted(snapshotId, snapshotFile);

    log.error(
        "ETL snapshot ingestion failed: {}, payload={}",
        context,
        failedMessage.getPayload(),
        rootCause
    );

    if (rootCause != null) {
      exceptionMessageService.logEtlError(rootCause);
    }
  }

  private void notifyOrchestratorAboutEarlySourceFailure(
      MessageHeaders headers,
      Throwable cause
  ) {
    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = headers.get(HDR_ETL_EVENT, String.class);
    MarketplaceEvent event =
        eventValue != null ? MarketplaceEvent.fromString(eventValue) : null;
    MarketplaceType marketplace =
        headers.get(HDR_ETL_SOURCE_MP, MarketplaceType.class);
    String sourceId = headers.get(HDR_ETL_SOURCE_ID, String.class);

    if (requestId != null
        && accountId != null
        && event != null
        && marketplace != null
        && sourceId != null) {
      orchestratorFlowConfig.markSourceFailedBeforeSnapshot(
          requestId,
          accountId,
          event,
          marketplace,
          sourceId
      );

      log.warn(
          "ETL source failed before snapshot registration; "
              + "plan will be marked as failed: requestId={}, accountId={}, event={}, marketplace={}, sourceId={}",
          requestId,
          accountId,
          event,
          marketplace,
          sourceId,
          cause
      );
    } else {
      log.warn(
          "ETL source failed before snapshot registration but context is incomplete; "
              + "cannot notify orchestrator reliably. headers={}",
          headers,
          cause
      );
    }
  }

  private void validateRequiredEtlHeaders(MessageHeaders headers) {
    List<String> missing = new ArrayList<>();

    if (headers.get(HDR_ETL_ACCOUNT_ID, Long.class) == null) {
      missing.add("accountId");
    }
    if (headers.get(HDR_ETL_SOURCE_MP, MarketplaceType.class) == null) {
      missing.add("marketplace");
    }
    if (headers.get(HDR_ETL_SNAPSHOT_ID, String.class) == null) {
      missing.add("snapshotId");
    }
    if (headers.get(HDR_ETL_REQUEST_ID, String.class) == null) {
      missing.add("requestId");
    }
    if (headers.get(HDR_ETL_EVENT, String.class) == null) {
      missing.add("event");
    }

    if (!missing.isEmpty()) {
      throw new AppException(ETL_CONTEXT_MISSING, String.join(", ", missing));
    }
  }
}
