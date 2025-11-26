package io.datapulse.etl.handler.error;

import io.datapulse.etl.dto.EtlSnapshotContext;
import io.datapulse.etl.file.SnapshotCommitBarrier;
import io.datapulse.etl.file.SnapshotFileCleaner;
import io.datapulse.etl.flow.EtlSnapshotContextExtractor;
import io.datapulse.etl.i18n.ExceptionMessageService;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class EtlSnapshotErrorHandler {

  private static final String STAGE_SNAPSHOT_PERSIST = "snapshot-persist";
  private static final String REASON_NO_SNAPSHOT_ID = "persist-error-without-snapshot-id";

  private final SnapshotCommitBarrier snapshotCommitBarrier;
  private final SnapshotFileCleaner snapshotFileCleaner;
  private final ExceptionMessageService exceptionMessageService;
  private final EtlSnapshotContextExtractor snapshotContextExtractor;

  public void handlePersistError(
      Throwable throwable,
      Message<?> failedMessage
  ) {
    MessageHeaders headers = failedMessage.getHeaders();

    EtlSnapshotContext context = snapshotContextExtractor.extract(headers);

    cleanUpSnapshotContext(context.snapshotId(), context.snapshotFile());

    logSnapshotError(
        throwable,
        context,
        STAGE_SNAPSHOT_PERSIST
    );
  }

  public void logSnapshotError(
      Throwable throwable,
      EtlSnapshotContext context,
      String stage
  ) {
    String message = exceptionMessageService.userMessage(throwable);
    log.error(
        """
            ETL snapshot error: {}
              stage={}
              requestId={}
              accountId={}
              event={}
              marketplace={}
              sourceId={}
              snapshotId={}
              snapshotFile={}
            """,
        message,
        stage,
        context.requestId(),
        context.accountId(),
        context.event(),
        context.marketplace(),
        context.sourceId(),
        context.snapshotId(),
        context.snapshotFile(),
        throwable
    );
  }

  private void cleanUpSnapshotContext(
      String snapshotId,
      Path snapshotFile
  ) {
    if (snapshotId != null) {
      snapshotCommitBarrier.discard(snapshotId);
      log.debug("Snapshot discarded due to persist error: snapshotId={}", snapshotId);
      return;
    }

    if (snapshotFile != null) {
      snapshotFileCleaner.deleteSafely(snapshotFile, REASON_NO_SNAPSHOT_ID);
      return;
    }

    log.warn(
        "Snapshot persist error without context: no snapshotId and no snapshotFile"
    );
  }
}
