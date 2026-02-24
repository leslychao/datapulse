package io.datapulse.etl.v1.execution;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.event.EventSource;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EtlExecutionWorkerTxServiceTest {

  @Test
  void retrySchedulingRespectsMaxAttemptsAndWritesOutbox() {
    EtlExecutionStateRepository state = mock(EtlExecutionStateRepository.class);
    EtlSourceRegistry registry = mock(EtlSourceRegistry.class);
    io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository raw = mock(io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository.class);
    EtlIngestUseCase ingestUseCase = mock(EtlIngestUseCase.class);
    EtlExecutionOutboxRepository outbox = mock(EtlExecutionOutboxRepository.class);

    EtlExecutionWorkerTxService service = new EtlExecutionWorkerTxService(state, registry, raw, ingestUseCase, outbox, new EtlExecutionPayloadCodec());
    EtlSourceExecution execution = new EtlSourceExecution("req-1", 1L, MarketplaceEvent.SALES_FACT, "src", LocalDate.now(), LocalDate.now());

    when(state.findExecution("req-1")).thenReturn(java.util.Optional.of(new EtlExecutionStateRepository.ExecutionRow("req-1", ExecutionStatus.IN_PROGRESS,1,0,0)));
    when(state.findSourceState("req-1", MarketplaceEvent.SALES_FACT, "src")).thenReturn(java.util.Optional.of(new EtlExecutionStateRepository.SourceStateRow("req-1", MarketplaceEvent.SALES_FACT, "src", SourceStateStatus.NEW,0,1)));
    when(state.markSourceInProgress("req-1", MarketplaceEvent.SALES_FACT, "src")).thenReturn(true);
    when(registry.getSources(MarketplaceEvent.SALES_FACT)).thenReturn(List.of(new EtlSourceRegistry.RegisteredSource(MarketplaceEvent.SALES_FACT, MarketplaceType.OZON,1,"src",mock(EventSource.class),"raw_table")));
    doThrow(new LocalRateLimitBackoffRequiredException("slow", 1000)).when(ingestUseCase).ingest(any());
    when(state.scheduleRetry(anyString(), any(), anyString(), anyString(), anyString(), any())).thenReturn(true).thenReturn(false);

    service.process(execution);
    verify(outbox).enqueueWait(eq(execution), anyString(), eq(1000L));

    service.process(execution);
    verify(state).markSourceFailedTerminal(eq("req-1"), eq(MarketplaceEvent.SALES_FACT), eq("src"), eq("LOCAL_RATE_LIMIT"), anyString());
  }

  @Test
  void successPathDeletesRawByRequestIdThenInvokesIngest() {
    EtlExecutionStateRepository state = mock(EtlExecutionStateRepository.class);
    EtlSourceRegistry registry = mock(EtlSourceRegistry.class);
    io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository raw = mock(io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository.class);
    EtlIngestUseCase ingestUseCase = mock(EtlIngestUseCase.class);
    EtlExecutionOutboxRepository outbox = mock(EtlExecutionOutboxRepository.class);

    EtlExecutionWorkerTxService service = new EtlExecutionWorkerTxService(state, registry, raw, ingestUseCase, outbox, new EtlExecutionPayloadCodec());
    EtlSourceExecution execution = new EtlSourceExecution("req-ok", 7L, MarketplaceEvent.SALES_FACT, "src", LocalDate.now(), LocalDate.now());

    when(state.findExecution("req-ok")).thenReturn(java.util.Optional.of(new EtlExecutionStateRepository.ExecutionRow("req-ok", ExecutionStatus.IN_PROGRESS,1,0,0)));
    when(state.findSourceState("req-ok", MarketplaceEvent.SALES_FACT, "src")).thenReturn(java.util.Optional.of(new EtlExecutionStateRepository.SourceStateRow("req-ok", MarketplaceEvent.SALES_FACT, "src", SourceStateStatus.NEW,0,5)));
    when(state.markSourceInProgress("req-ok", MarketplaceEvent.SALES_FACT, "src")).thenReturn(true);
    when(registry.getSources(MarketplaceEvent.SALES_FACT)).thenReturn(List.of(new EtlSourceRegistry.RegisteredSource(MarketplaceEvent.SALES_FACT, MarketplaceType.OZON,1,"src",mock(EventSource.class),"raw_table")));
    doNothing().when(ingestUseCase).ingest(any());

    service.process(execution);

    var inOrder = inOrder(raw, ingestUseCase, state);
    inOrder.verify(raw).deleteByRequestId("raw_table", "req-ok");
    inOrder.verify(ingestUseCase).ingest(any());
    inOrder.verify(state).markSourceCompleted("req-ok", MarketplaceEvent.SALES_FACT, "src");
  }
}
