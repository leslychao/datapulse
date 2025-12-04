package io.datapulse.etl.nextgen.service;

import io.datapulse.etl.nextgen.dto.EventAuditRecord;
import io.datapulse.etl.nextgen.dto.EventStatus;
import io.datapulse.etl.nextgen.dto.ExecutionAuditRecord;
import io.datapulse.etl.nextgen.dto.ExecutionStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final List<EventAuditRecord> eventAudits = new ArrayList<>();
  private final List<ExecutionAuditRecord> executionAudits = new ArrayList<>();

  public void recordExecution(
      UUID executionId,
      String eventId,
      String marketplace,
      String sourceName,
      ExecutionStatus status,
      long rawRows,
      int retryCount,
      String errorCode,
      String errorMessage
  ) {
    ExecutionAuditRecord record = new ExecutionAuditRecord(
        executionId,
        eventId,
        marketplace,
        sourceName,
        status,
        rawRows,
        retryCount,
        errorCode,
        errorMessage,
        OffsetDateTime.now()
    );
    executionAudits.add(record);
    log.info("[audit] execution {} status {} rawRows {} retries {}", executionId, status, rawRows, retryCount);
  }

  public void recordEvent(
      String eventId,
      Long accountId,
      String eventType,
      EventStatus status,
      OffsetDateTime startedAt,
      OffsetDateTime finishedAt,
      long totalRawRows,
      int success,
      int noData,
      int failed
  ) {
    EventAuditRecord record = new EventAuditRecord(
        eventId,
        accountId,
        eventType,
        status,
        startedAt,
        finishedAt,
        totalRawRows,
        success,
        noData,
        failed
    );
    eventAudits.add(record);
    log.info("[audit] event {} status {} success {} noData {} failed {}", eventId, status, success, noData, failed);
  }

  public List<EventAuditRecord> eventAudits() {
    return List.copyOf(eventAudits);
  }

  public List<ExecutionAuditRecord> executionAudits() {
    return List.copyOf(executionAudits);
  }
}
