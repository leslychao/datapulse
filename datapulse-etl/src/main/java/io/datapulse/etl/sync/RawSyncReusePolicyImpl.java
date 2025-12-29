package io.datapulse.etl.sync;

import static java.time.OffsetDateTime.now;

import io.datapulse.core.service.EtlExecutionAuditService;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public final class RawSyncReusePolicyImpl implements RawSyncReusePolicy {

  private static final Duration DEFAULT_REUSE_TTL = Duration.ofMinutes(60);

  private static final Set<SyncStatus> REUSABLE_STATUSES =
      EnumSet.of(SyncStatus.SUCCESS, SyncStatus.NO_DATA);

  private final EtlExecutionAuditService auditService;

  @Override
  public boolean requiresSync(EtlSourceExecution execution) {
    Optional<EtlExecutionAuditDto> latestAudit = auditService.findLatestSync(
        execution.accountId(),
        execution.marketplace(),
        execution.sourceId(),
        execution.dateFrom(),
        execution.dateTo()
    );

    boolean requiresSync = latestAudit
        .map(this::isNotReusable)
        .orElse(true);

    if (log.isDebugEnabled()) {
      if (latestAudit.isEmpty()) {
        log.debug(
            "Raw sync required: audit entry not found for reuse: " +
                "requestId={}, accountId={}, marketplace={}, sourceId={}, dateFrom={}, dateTo={}",
            execution.requestId(),
            execution.accountId(),
            execution.marketplace(),
            execution.sourceId(),
            execution.dateFrom(),
            execution.dateTo()
        );
      } else {
        EtlExecutionAuditDto audit = latestAudit.get();
        log.debug(
            "Raw sync decision: requiresSync={}, auditStatus={}, auditCreatedAt={}, " +
                "requestId={}, accountId={}, marketplace={}, sourceId={}, dateFrom={}, dateTo={}",
            requiresSync,
            audit.getStatus(),
            audit.getCreatedAt(),
            execution.requestId(),
            execution.accountId(),
            execution.marketplace(),
            execution.sourceId(),
            execution.dateFrom(),
            execution.dateTo()
        );
      }
    }

    return requiresSync;
  }

  @Override
  public ExecutionOutcome reuseFromAudit(EtlSourceExecution execution) {
    EtlExecutionAuditDto audit = auditService
        .findLatestSync(
            execution.accountId(),
            execution.marketplace(),
            execution.sourceId(),
            execution.dateFrom(),
            execution.dateTo()
        )
        .filter(this::isReusable)
        .orElseThrow(() -> {
          log.warn(
              "Raw sync reuse requested, but reusable audit entry not found: " +
                  "requestId={}, accountId={}, marketplace={}, sourceId={}, dateFrom={}, dateTo={}",
              execution.requestId(),
              execution.accountId(),
              execution.marketplace(),
              execution.sourceId(),
              execution.dateFrom(),
              execution.dateTo()
          );
          return new AppException(
              "ETL_RAW_SYNC_REUSE_ENTRY_NOT_FOUND",
              execution.sourceId()
          );
        });

    long rowsCount = audit.getRowsCount() != null ? audit.getRowsCount() : 0L;
    IngestStatus status = rowsCount > 0L ? IngestStatus.SUCCESS : IngestStatus.NO_DATA;

    String reusedRequestId = execution.requestId();

    log.info(
        "Raw sync reused from audit: orchestrationRequestId={}, rawSyncId={}, " +
            "accountId={}, marketplace={}, sourceId={}, event={}, " +
            "dateFrom={}, dateTo={}, status={}, rowsCount={}, auditCreatedAt={}",
        execution.requestId(),
        audit.getRawSyncId(),
        execution.accountId(),
        execution.marketplace(),
        audit.getSourceId(),
        execution.event(),
        execution.dateFrom(),
        execution.dateTo(),
        status,
        rowsCount,
        audit.getCreatedAt()
    );

    return new ExecutionOutcome(
        reusedRequestId,
        audit.getRawSyncId(),
        execution.accountId(),
        audit.getSourceId(),
        execution.marketplace(),
        execution.event(),
        execution.dateFrom(),
        execution.dateTo(),
        status,
        rowsCount,
        null,
        null
    );
  }

  private boolean isNotReusable(EtlExecutionAuditDto audit) {
    return !isReusable(audit);
  }

  private boolean isReusable(EtlExecutionAuditDto audit) {
    if (audit.getStatus() == null || !REUSABLE_STATUSES.contains(audit.getStatus())) {
      return false;
    }

    OffsetDateTime createdAt = audit.getCreatedAt();
    if (createdAt == null) {
      return false;
    }

    OffsetDateTime threshold = now().minus(DEFAULT_REUSE_TTL);
    return !createdAt.isBefore(threshold);
  }
}
