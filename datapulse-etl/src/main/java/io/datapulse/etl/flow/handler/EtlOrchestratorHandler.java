package io.datapulse.etl.flow.handler;

import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.flow.repository.EtlExecutionRepo;
import io.datapulse.etl.flow.repository.EtlSourceStateRepo;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
public class EtlOrchestratorHandler {

  private final EtlSourceRegistry registry;
  private final EtlExecutionRepo execRepo;
  private final EtlSourceStateRepo stateRepo;

  public EtlOrchestratorHandler(EtlSourceRegistry registry, EtlExecutionRepo execRepo, EtlSourceStateRepo stateRepo) {
    this.registry = registry;
    this.execRepo = execRepo;
    this.stateRepo = stateRepo;
  }

  public List<EtlSourceExecution> handle(Message<EtlRunRequest> msg) {
    EtlRunRequest req = msg.getPayload();

    String requestId = (String) msg.getHeaders().get("requestId");
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("Missing header requestId");
    }

    execRepo.upsertExecutionNewIfAbsent(requestId);

    var sources = registry.getSources(req.event());

    stateRepo.ensureStatesExist(requestId, req, sources);

    execRepo.markInProgressAndTotalSources(requestId, sources.size());

    return sources.stream()
        .map(s -> new EtlSourceExecution(
            requestId,
            req.accountId(),
            req.event(),
            s.sourceId(),
            req.dateFrom(),
            req.dateTo(),
            null
        ))
        .toList();
  }
}