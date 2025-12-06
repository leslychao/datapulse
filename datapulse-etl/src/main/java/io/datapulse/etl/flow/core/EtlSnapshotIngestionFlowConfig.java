package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_INGEST_JSON_LAYOUT_NOT_FOUND;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_ELEMENT_TYPE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_FILE_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_INGEST_SNAPSHOT_REQUIRED;
import static io.datapulse.etl.flow.core.EtlFlowConstants.CH_ETL_INGEST;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.file.IngestItemIterator;
import io.datapulse.etl.file.SnapshotIteratorFactory;
import io.datapulse.etl.file.locator.JsonArrayLocator;
import io.datapulse.etl.file.locator.SnapshotJsonLayoutRegistry;
import io.datapulse.etl.repository.RawBatchInsertJdbcRepository;
import io.datapulse.marketplaces.dto.Snapshot;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.util.CloseableIterator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EtlSnapshotIngestionFlowConfig {

  private static final int SNAPSHOT_BATCH_SIZE = 500;

  private final SnapshotJsonLayoutRegistry snapshotJsonLayoutRegistry;
  private final SnapshotIteratorFactory snapshotIteratorFactory;
  private final RawBatchInsertJdbcRepository repository;

  @Bean
  public IntegrationFlow etlIngestFlow(Advice ingestErrorAdvice) {
    return IntegrationFlow
        .from(CH_ETL_INGEST)
        .handle(
            EtlSourceExecution.class,
            this::fetchSnapshotAndBuildContext,
            endpoint -> endpoint.advice(ingestErrorAdvice)
        )
        .log(message -> {
          IngestContext ingestContext = (IngestContext) message.getPayload();
          EtlSourceExecution execution = ingestContext.execution();
          MessageHeaders headers = message.getHeaders();
          String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);

          return String.format(
              "ETL snapshot ready for ingest: requestId=%s, accountId=%s, event=%s, " +
                  "marketplace=%s, sourceId=%s, rawTable=%s, file=%s",
              requestId,
              execution.accountId(),
              execution.event(),
              execution.marketplace(),
              execution.sourceId(),
              execution.rawTable(),
              ingestContext.snapshotFile()
          );
        })
        .split(
            IngestContext.class,
            this::toIngestItemIterator
        )
        .aggregate(aggregator -> aggregator
            .correlationStrategy(message -> {
              Object payload = message.getPayload();
              if (payload instanceof IngestItem<?> ingestItem) {
                EtlSourceExecution e = ingestItem.context().execution();
                return String.format(
                    "%s:%s:%s:%s:%s",
                    message.getHeaders().get(HDR_ETL_REQUEST_ID, String.class),
                    e.accountId(),
                    e.event(),
                    e.marketplace(),
                    e.sourceId()
                );
              }
              throw new IllegalStateException(
                  "Unexpected payload type in correlationStrategy: " + payload.getClass().getName()
              );
            })
            .releaseStrategy(group -> group.size() >= SNAPSHOT_BATCH_SIZE)
            .groupTimeout(5_000L)
            .outputProcessor(group -> {
              Message<?> sampleMessage = group.getOne();
              Object samplePayload = sampleMessage.getPayload();
              if (!(samplePayload instanceof IngestItem<?> sampleItem)) {
                throw new IllegalStateException(
                    "Unexpected payload type in outputProcessor: " + samplePayload.getClass()
                        .getName()
                );
              }

              IngestContext context = sampleItem.context();

              List<?> rows = group.getMessages()
                  .stream()
                  .map(Message::getPayload)
                  .map(payload -> {
                    if (!(payload instanceof IngestItem<?> ingestItem)) {
                      throw new IllegalStateException(
                          "Unexpected payload type in batch group: " + payload.getClass().getName()
                      );
                    }
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
            endpoint -> endpoint
                .advice(ingestErrorAdvice)
                .requiresReply(false)
        )
        .get();
  }

  private IngestContext fetchSnapshotAndBuildContext(
      EtlSourceExecution execution,
      Map<String, Object> headersMap
  ) {
    Snapshot<?> snapshot = execution
        .source()
        .fetchSnapshot(
            execution.accountId(),
            execution.event(),
            execution.from(),
            execution.to()
        );

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

    MessageHeaders headers = new MessageHeaders(headersMap);
    String requestId = headers.get(HDR_ETL_REQUEST_ID, String.class);

    IngestContext context = batch.context();
    EtlSourceExecution execution = context.execution();

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
      T row
  ) {

  }

  public record IngestBatch<T>(
      IngestContext context,
      List<T> rows
  ) {

  }
}
