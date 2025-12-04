package io.datapulse.etl.flow.audit;

import static io.datapulse.etl.flow.core.FlowChannels.CH_EVENT_AUDIT;

import io.datapulse.core.service.EtlSyncAuditService;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import io.datapulse.etl.flow.core.model.EventAggregation;
import io.datapulse.etl.flow.core.model.EventStatus;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

@Configuration
public class AuditFlowConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(AuditFlowConfiguration.class);

  private final EtlSyncAuditService etlSyncAuditService;

  public AuditFlowConfiguration(EtlSyncAuditService etlSyncAuditService) {
    this.etlSyncAuditService = etlSyncAuditService;
  }

  @Bean
  public IntegrationFlow auditFlow() {
    return IntegrationFlow.from(CH_EVENT_AUDIT)
        .filter(EventAggregation.class::isInstance)
        .transform(EventAggregation.class, this::persistAudit)
        .get();
  }

  private EventAggregation persistAudit(EventAggregation aggregation) {
    if (aggregation == null || aggregation.accountId() == null || aggregation.event() == null) {
      return aggregation;
    }
    EtlSyncAuditDto dto = new EtlSyncAuditDto();
    dto.setRequestId(aggregation.requestId());
    dto.setAccountId(aggregation.accountId());
    dto.setEvent(aggregation.event().name());
    dto.setDateFrom(aggregation.from());
    dto.setDateTo(aggregation.to());
    dto.setStatus(mapStatus(aggregation.status()));
    dto.setFailedSources(String.join(",", aggregation.failedSources()));
    if (aggregation.status() == EventStatus.NO_DATA) {
      dto.setErrorMessage("NO_DATA");
    }
    etlSyncAuditService.save(dto);
    LOG.info(
        "Audit event updated: requestId={}, status={}, failedSources={}",
        aggregation.requestId(),
        aggregation.status(),
        aggregation.failedSources()
    );
    return aggregation;
  }

  private SyncStatus mapStatus(EventStatus status) {
    return switch (status) {
      case WAITING -> SyncStatus.WAIT;
      case IN_PROGRESS, PENDING -> SyncStatus.IN_PROGRESS;
      case SUCCESS -> SyncStatus.SUCCESS;
      case PARTIAL_SUCCESS -> SyncStatus.PARTIAL_SUCCESS;
      case NO_DATA -> SyncStatus.SUCCESS;
      case ERROR -> SyncStatus.ERROR;
    };
  }
}
