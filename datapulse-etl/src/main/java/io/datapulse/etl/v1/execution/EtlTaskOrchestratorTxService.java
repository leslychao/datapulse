package io.datapulse.etl.v1.execution;

import io.datapulse.core.service.account.AccountConnectionService;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.event.EtlSourceRegistry.RegisteredSource;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import io.datapulse.etl.v1.dto.RunTask;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;

@Service
@RequiredArgsConstructor
public class EtlTaskOrchestratorTxService {

  private final EtlSourceRegistry sourceRegistry;
  private final AccountConnectionService accountConnectionService;
  private final EtlExecutionStateRepository stateRepository;
  private final RabbitTemplate rabbitTemplate;

  @Value("${etl.execution.max-attempts:5}")
  private int maxAttempts;

  @Transactional
  public void orchestrate(RunTask task) {
    String requestId = UUID.randomUUID().toString();
    List<RegisteredSource> sources = sourceRegistry.getSources(task.event()).stream()
        .filter(source -> accountConnectionService.getActiveMarketplacesByAccountId(task.accountId())
            .contains(source.marketplace()))
        .toList();

    stateRepository.insertExecution(requestId, task.accountId(), task.event(), task.dateFrom(), task.dateTo(), sources.size());
    stateRepository.insertSourceStates(requestId, task.event(), sources.stream().map(RegisteredSource::sourceId).toList(), maxAttempts);

    for (RegisteredSource source : sources) {
      rabbitTemplate.convertAndSend(EXCHANGE_EXECUTION, ROUTING_KEY_EXECUTION,
          new EtlSourceExecution(requestId, task.accountId(), task.event(), source.sourceId(), task.dateFrom(), task.dateTo()));
    }

    stateRepository.markExecutionInProgress(requestId);
  }
}
