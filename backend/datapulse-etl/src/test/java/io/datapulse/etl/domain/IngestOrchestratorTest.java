package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.config.IngestProperties.PostIngestMaterializationMode;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.platform.etl.PostIngestMaterializationHook;
import io.datapulse.platform.etl.PostIngestMaterializationResult;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class IngestOrchestratorTest {

  @Mock private JobExecutionRepository jobExecutionRepository;
  @Mock private CredentialResolver credentialResolver;
  @Mock private CheckpointManager checkpointManager;
  @Mock private DagExecutor dagExecutor;
  @Mock private OutboxService outboxService;
  @Mock private IngestResultReporter resultReporter;
  @Mock private StaleCampaignDetector staleCampaignDetector;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private PostIngestMaterializationHook postIngestMaterialization;
  @Mock private MarketplaceSyncStateRepository marketplaceSyncStateRepository;

  private IngestOrchestrator orchestrator;

  private final IngestProperties ingestProperties = new IngestProperties(
      500, 5000, Duration.ofHours(2), 3,
      Duration.ofMinutes(5), Duration.ofMinutes(20), 2,
      Duration.ofHours(1), Duration.ofHours(48), 30,
      365, Duration.ofHours(1),
      Duration.ofHours(6),
      Duration.ofMinutes(15),
      Duration.ofHours(1),
      PostIngestMaterializationMode.SYNC,
      Duration.ofMinutes(15));

  private final Clock ingestClock =
      Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    IngestJobAcquisitionService acquisition =
        new IngestJobAcquisitionService(jobExecutionRepository, ingestProperties, ingestClock);
    lenient().when(marketplaceSyncStateRepository.findAllByMarketplaceConnectionId(anyLong()))
        .thenReturn(List.of());
    IngestSyncContextBuilder contextBuilder =
        new IngestSyncContextBuilder(
            credentialResolver,
            checkpointManager,
            new ObjectMapper(),
            marketplaceSyncStateRepository,
            ingestProperties,
            ingestClock);
    IngestJobCompletionCoordinator completion = new IngestJobCompletionCoordinator(
        jobExecutionRepository,
        checkpointManager,
        resultReporter,
        staleCampaignDetector,
        ingestProperties,
        transactionTemplate,
        postIngestMaterialization,
        outboxService);

    orchestrator = new IngestOrchestrator(
        jobExecutionRepository,
        dagExecutor,
        resultReporter,
        acquisition,
        contextBuilder,
        completion);

    lenient().doAnswer(inv -> {
      Consumer<TransactionStatus> action = inv.getArgument(0);
      action.accept(null);
      return null;
    }).when(transactionTemplate).executeWithoutResult(any());

    lenient().when(postIngestMaterialization.afterSuccessfulIngest(anyLong()))
        .thenReturn(PostIngestMaterializationResult.ok());
    lenient().when(resultReporter.mergeMaterializationIntoErrorDetails(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Nested
  @DisplayName("processSync()")
  class ProcessSync {

    @Test
    void should_acquireJob_and_executeDag_when_jobIsPending() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(0);

      Map<EtlEventType, EventResult> results = allCompletedResults();
      when(dagExecutor.execute(any())).thenReturn(results);
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");

      orchestrator.processSync(1L);

      verify(dagExecutor).execute(any());
      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.MATERIALIZING);
      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED);
      verify(postIngestMaterialization).afterSuccessfulIngest(1L);
    }

    @Test
    void should_completeWithErrors_when_materializationFailsAfterCleanIngest() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(0);
      when(dagExecutor.execute(any())).thenReturn(allCompletedResults());
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");
      when(postIngestMaterialization.afterSuccessfulIngest(1L)).thenReturn(
          PostIngestMaterializationResult.partialFailure(List.of("mart_posting_pnl")));

      orchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.MATERIALIZING);
      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED_WITH_ERRORS);
    }

    @Test
    void should_skipProcessing_when_casAcquireFails() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(false);

      orchestrator.processSync(1L);

      verify(dagExecutor, never()).execute(any());
      verify(postIngestMaterialization, never()).afterSuccessfulIngest(anyLong());
    }

    @Test
    void should_skipProcessing_when_inProgressAndNotRedelivered_and_startedRecently() {
      JobExecutionRow job = JobExecutionRow.builder()
          .id(1L)
          .connectionId(100L)
          .eventType("FULL_SYNC")
          .status("IN_PROGRESS")
          .startedAt(OffsetDateTime.parse("2024-06-15T11:50:00Z"))
          .build();
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));

      orchestrator.processSync(1L, false);

      verify(dagExecutor, never()).execute(any());
      verify(jobExecutionRepository, never()).reclaimInProgressAfterBrokerRedelivery(anyLong());
    }

    @Test
    void should_resumeAndExecuteDag_when_inProgressAndRabbitRedelivered() {
      JobExecutionRow job = buildJob(1L, "IN_PROGRESS");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.reclaimInProgressAfterBrokerRedelivery(1L)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(0);
      Map<EtlEventType, EventResult> results = allCompletedResults();
      when(dagExecutor.execute(any())).thenReturn(results);
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");

      orchestrator.processSync(1L, true);

      verify(jobExecutionRepository).reclaimInProgressAfterBrokerRedelivery(1L);
      verify(dagExecutor).execute(any());
      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.MATERIALIZING);
    }

    @Test
    void should_resume_when_inProgressOrphanOlderThanThresholdWithoutRedelivery() {
      JobExecutionRow job = JobExecutionRow.builder()
          .id(1L)
          .connectionId(100L)
          .eventType("FULL_SYNC")
          .status("IN_PROGRESS")
          .startedAt(OffsetDateTime.parse("2024-06-15T11:30:00Z"))
          .build();
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.reclaimInProgressAfterBrokerRedelivery(1L)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(0);
      when(dagExecutor.execute(any())).thenReturn(allCompletedResults());
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");

      orchestrator.processSync(1L, false);

      verify(jobExecutionRepository).reclaimInProgressAfterBrokerRedelivery(1L);
      verify(dagExecutor).execute(any());
    }

    @Test
    void should_markFailed_when_jobNotFound() {
      when(jobExecutionRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> orchestrator.processSync(99L))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_markFailed_when_dagThrowsException() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(dagExecutor.execute(any())).thenThrow(new RuntimeException("S3 unavailable"));

      orchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.FAILED);
      verify(jobExecutionRepository).updateErrorDetails(eq(1L), anyString());
      verify(postIngestMaterialization, never()).afterSuccessfulIngest(anyLong());
    }

    @Test
    void should_scheduleRetry_when_retriableFailureAndRetriesRemaining() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(0);
      when(checkpointManager.serialize(any(), eq(1))).thenReturn("{}");
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");

      Map<EtlEventType, EventResult> results = resultsWithOneFailed();
      when(dagExecutor.execute(any())).thenReturn(results);

      orchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.RETRY_SCHEDULED);
      verify(outboxService).createEvent(any(), any(), anyLong(), any());
      verify(postIngestMaterialization, never()).afterSuccessfulIngest(anyLong());
    }

    @Test
    void should_completeFailed_when_retriableFailureAndRetriesExhausted() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(3);
      when(checkpointManager.serialize(any(), eq(3))).thenReturn("{}");
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");

      Map<EtlEventType, EventResult> results = resultsWithOneFailed();
      when(dagExecutor.execute(any())).thenReturn(results);

      orchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.MATERIALIZING);
      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED_WITH_ERRORS);
      verify(outboxService, never()).createEvent(any(), any(), anyLong(), any());
      verify(postIngestMaterialization).afterSuccessfulIngest(1L);
    }
  }

  @Nested
  @DisplayName("determineFinalStatus via processSync")
  class DetermineFinalStatus {

    @BeforeEach
    void setUpCommon() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(0);
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");
    }

    @Test
    void should_returnCompleted_when_allEventsSucceed() {
      when(dagExecutor.execute(any())).thenReturn(allCompletedResults());

      orchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.MATERIALIZING);
      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED);
      verify(postIngestMaterialization).afterSuccessfulIngest(1L);
    }

    @Test
    void should_returnCompletedWithErrors_when_someEventsFailed() {
      Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
      for (EtlEventType type : EtlEventType.values()) {
        results.put(type, EventResult.completed(type, List.of()));
      }
      results.put(EtlEventType.SALES_FACT,
          EventResult.completedWithErrors(EtlEventType.SALES_FACT, List.of()));
      when(dagExecutor.execute(any())).thenReturn(results);

      orchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.MATERIALIZING);
      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED_WITH_ERRORS);
      verify(postIngestMaterialization).afterSuccessfulIngest(1L);
    }

    @Test
    void should_returnFailed_when_allEventsFailed() {
      Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
      for (EtlEventType type : EtlEventType.values()) {
        results.put(type, EventResult.failed(type, List.of()));
      }
      when(dagExecutor.execute(any())).thenReturn(results);

      orchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.FAILED);
      verify(postIngestMaterialization, never()).afterSuccessfulIngest(anyLong());
    }
  }

  @Nested
  @DisplayName("post-ingest materialization ASYNC_OUTBOX")
  class AsyncPostIngestMaterialization {

    private IngestOrchestrator asyncOrchestrator;

    @BeforeEach
    void setUpAsync() {
      IngestProperties asyncProps = new IngestProperties(
          500, 5000, Duration.ofHours(2), 3,
          Duration.ofMinutes(5), Duration.ofMinutes(20), 2,
          Duration.ofHours(1), Duration.ofHours(48), 30,
          365, Duration.ofHours(1),
          Duration.ofHours(6),
          Duration.ofMinutes(15),
          Duration.ofHours(1),
          PostIngestMaterializationMode.ASYNC_OUTBOX,
          Duration.ofMinutes(15));
      IngestJobAcquisitionService acquisition =
          new IngestJobAcquisitionService(jobExecutionRepository, asyncProps, ingestClock);
      IngestSyncContextBuilder contextBuilder =
          new IngestSyncContextBuilder(
              credentialResolver,
              checkpointManager,
              new ObjectMapper(),
              marketplaceSyncStateRepository,
              asyncProps,
              ingestClock);
      IngestJobCompletionCoordinator completion = new IngestJobCompletionCoordinator(
          jobExecutionRepository,
          checkpointManager,
          resultReporter,
          staleCampaignDetector,
          asyncProps,
          transactionTemplate,
          postIngestMaterialization,
          outboxService);
      asyncOrchestrator = new IngestOrchestrator(
          jobExecutionRepository,
          dagExecutor,
          resultReporter,
          acquisition,
          contextBuilder,
          completion);
    }

    @Test
    void should_enqueuePostIngestMaterialize_and_notFinalizeInline_when_asyncMode() {
      JobExecutionRow job = buildJob(1L, "PENDING");
      when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
      when(jobExecutionRepository.casStatus(1L, JobExecutionStatus.PENDING,
          JobExecutionStatus.IN_PROGRESS)).thenReturn(true);
      when(credentialResolver.resolve(100L)).thenReturn(buildCredentials());
      when(checkpointManager.parse(any())).thenReturn(Map.of());
      when(checkpointManager.extractRetryCount(any())).thenReturn(0);
      when(dagExecutor.execute(any())).thenReturn(allCompletedResults());
      when(resultReporter.buildErrorDetails(any())).thenReturn("{}");

      asyncOrchestrator.processSync(1L);

      verify(jobExecutionRepository).casStatus(
          1L, JobExecutionStatus.IN_PROGRESS, JobExecutionStatus.MATERIALIZING);
      verify(outboxService).createEvent(
          eq(OutboxEventType.ETL_POST_INGEST_MATERIALIZE),
          eq("job_execution"),
          eq(1L),
          any());
      verify(postIngestMaterialization, never()).afterSuccessfulIngest(anyLong());
      verify(jobExecutionRepository, never()).casStatus(
          eq(1L), eq(JobExecutionStatus.MATERIALIZING), any());
    }
  }

  private JobExecutionRow buildJob(long id, String status) {
    return JobExecutionRow.builder()
        .id(id)
        .connectionId(100L)
        .eventType("FULL_SYNC")
        .status(status)
        .build();
  }

  private CredentialResolver.ResolvedCredentials buildCredentials() {
    return new CredentialResolver.ResolvedCredentials(
        100L, 1L, MarketplaceType.WB, Map.of("apiToken", "test"));
  }

  private Map<EtlEventType, EventResult> allCompletedResults() {
    Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
    for (EtlEventType type : EtlEventType.values()) {
      results.put(type, EventResult.completed(type, List.of()));
    }
    return results;
  }

  private Map<EtlEventType, EventResult> resultsWithOneFailed() {
    Map<EtlEventType, EventResult> results = new EnumMap<>(EtlEventType.class);
    for (EtlEventType type : EtlEventType.values()) {
      results.put(type, EventResult.completed(type, List.of()));
    }
    results.put(EtlEventType.SALES_FACT, EventResult.failed(
        EtlEventType.SALES_FACT,
        List.of(SubSourceResult.failed("WbSalesFact", "API timeout"))));
    return results;
  }
}
