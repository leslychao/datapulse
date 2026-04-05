package io.datapulse.etl.domain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.platform.etl.PostIngestMaterializationHook;
import io.datapulse.platform.etl.PostIngestMaterializationResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PostIngestMaterializationMessageHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private JobExecutionRepository jobExecutionRepository;
  @Mock private PostIngestMaterializationHook postIngestMaterialization;
  @Mock private IngestResultReporter resultReporter;
  @Mock private StaleCampaignDetector staleCampaignDetector;
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private PostIngestMaterializationMessageHandler handler;

  @BeforeEach
  void setUp() {
    lenient().doAnswer(inv -> {
      Consumer<TransactionStatus> action = inv.getArgument(0);
      action.accept(null);
      return null;
    }).when(transactionTemplate).executeWithoutResult(any());
    lenient().when(resultReporter.mergeMaterializationIntoErrorDetails(anyString(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void should_skip_when_jobNotInMaterializing() throws Exception {
    JsonNode payload = objectMapper.readTree(
        "{\"jobExecutionId\":1,\"workspaceId\":2,\"connectionId\":3,\"syncScope\":\"FULL_SYNC\","
            + "\"ingestStatus\":\"COMPLETED\",\"completedDomains\":[],\"failedDomains\":[],"
            + "\"promoSyncCompleted\":false}");
    JobExecutionRow job =
        JobExecutionRow.builder().id(1L).connectionId(3L).status("COMPLETED").build();
    when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));

    handler.handle(payload);

    verify(postIngestMaterialization, never()).afterSuccessfulIngest(anyLong());
  }

  @Test
  void should_finalizeAndFanOut_when_materializingAndCasSucceeds() throws Exception {
    JsonNode payload = objectMapper.readTree(
        "{\"jobExecutionId\":1,\"workspaceId\":2,\"connectionId\":3,\"syncScope\":\"FULL_SYNC\","
            + "\"ingestStatus\":\"COMPLETED\",\"completedDomains\":[\"SALES_FACT\"],"
            + "\"failedDomains\":[],\"promoSyncCompleted\":true}");
    JobExecutionRow job =
        JobExecutionRow.builder()
            .id(1L)
            .connectionId(3L)
            .eventType("FULL_SYNC")
            .status("MATERIALIZING")
            .errorDetails("{}")
            .build();
    when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
    when(postIngestMaterialization.afterSuccessfulIngest(1L))
        .thenReturn(PostIngestMaterializationResult.ok());
    when(jobExecutionRepository.casStatus(
            1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED))
        .thenReturn(true);

    handler.handle(payload);

    verify(postIngestMaterialization).afterSuccessfulIngest(1L);
    verify(resultReporter)
        .recordSuccessfulTerminalSyncLists(
            eq(job), eq(2L), eq(List.of("SALES_FACT")), eq(List.of()));
    verify(staleCampaignDetector).detectAndPublish(3L);
  }

  @Test
  void should_notRunStaleDetector_when_promoNotCompleted() throws Exception {
    JsonNode payload = objectMapper.readTree(
        "{\"jobExecutionId\":1,\"workspaceId\":2,\"connectionId\":3,\"syncScope\":\"FULL_SYNC\","
            + "\"ingestStatus\":\"COMPLETED\",\"completedDomains\":[],\"failedDomains\":[],"
            + "\"promoSyncCompleted\":false}");
    JobExecutionRow job =
        JobExecutionRow.builder()
            .id(1L)
            .connectionId(3L)
            .eventType("FULL_SYNC")
            .status("MATERIALIZING")
            .errorDetails("{}")
            .build();
    when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
    when(postIngestMaterialization.afterSuccessfulIngest(1L))
        .thenReturn(PostIngestMaterializationResult.ok());
    when(jobExecutionRepository.casStatus(
            1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED))
        .thenReturn(true);

    handler.handle(payload);

    verify(staleCampaignDetector, never()).detectAndPublish(anyLong());
  }

  @Test
  void should_reconcileSyncState_when_casFails() throws Exception {
    JsonNode payload = objectMapper.readTree(
        "{\"jobExecutionId\":1,\"workspaceId\":2,\"connectionId\":3,\"syncScope\":\"FULL_SYNC\","
            + "\"ingestStatus\":\"COMPLETED\",\"completedDomains\":[],\"failedDomains\":[],"
            + "\"promoSyncCompleted\":false}");
    JobExecutionRow job =
        JobExecutionRow.builder()
            .id(1L)
            .connectionId(3L)
            .eventType("FULL_SYNC")
            .status("MATERIALIZING")
            .errorDetails("{}")
            .build();
    when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(job));
    when(postIngestMaterialization.afterSuccessfulIngest(1L))
        .thenReturn(PostIngestMaterializationResult.ok());
    when(jobExecutionRepository.casStatus(
            1L, JobExecutionStatus.MATERIALIZING, JobExecutionStatus.COMPLETED))
        .thenReturn(false);

    handler.handle(payload);

    verify(resultReporter, never())
        .recordSuccessfulTerminalSyncLists(any(), anyLong(), any(), any());
    verify(resultReporter).reconcileSyncingWhenNoActiveJob(3L);
  }
}
