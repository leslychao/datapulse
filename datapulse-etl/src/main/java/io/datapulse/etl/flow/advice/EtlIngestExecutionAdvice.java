package io.datapulse.etl.flow.advice;

import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.flow.core.handler.EtlIngestErrorHandler;
import io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlIngestExecutionAdvice extends EtlAbstractRequestHandlerAdvice {

  private final EtlIngestErrorHandler ingestErrorHandler;
  private final RawBatchInsertJdbcRepository rawBatchInsertJdbcRepository;

  @Override
  protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
    EtlSourceExecution execution = (EtlSourceExecution) message.getPayload();

    try {
      callback.execute();

      long rowsCount = rawBatchInsertJdbcRepository.countByRequestId(
          execution.rawTable(),
          execution.requestId()
      );

      IngestStatus status = rowsCount > 0L ? IngestStatus.SUCCESS : IngestStatus.NO_DATA;

      return new ExecutionOutcome(
          execution.requestId(),
          execution.requestId(),
          execution.accountId(),
          execution.sourceId(),
          execution.marketplace(),
          execution.event(),
          execution.dateFrom(),
          execution.dateTo(),
          status,
          rowsCount,
          null,
          null
      );
    } catch (Exception ex) {
      Throwable cause = unwrapProcessingError(ex);
      return ingestErrorHandler.handleIngestError(cause, execution);
    }
  }
}
