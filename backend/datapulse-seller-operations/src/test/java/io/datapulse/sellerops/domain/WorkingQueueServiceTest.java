package io.datapulse.sellerops.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.CreateQueueRequest;
import io.datapulse.sellerops.api.QueueSummaryResponse;
import io.datapulse.sellerops.api.UpdateQueueRequest;
import io.datapulse.sellerops.persistence.QueueItemSummaryJdbcRepository;
import io.datapulse.sellerops.persistence.WorkingQueueAssignmentEntity;
import io.datapulse.sellerops.persistence.WorkingQueueAssignmentRepository;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionEntity;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingQueueServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long QUEUE_ID = 10L;
  private static final long ITEM_ID = 100L;
  private static final long USER_ID = 5L;

  @Mock
  private WorkingQueueDefinitionRepository definitionRepository;
  @Mock
  private WorkingQueueAssignmentRepository assignmentRepository;
  @Mock
  private QueueItemSummaryJdbcRepository summaryRepository;

  @InjectMocks
  private WorkingQueueService service;

  @Nested
  @DisplayName("createQueue")
  class CreateQueue {

    @Test
    void should_create_queue_when_name_unique() {
      var request = new CreateQueueRequest("My Queue", "PRICING_REVIEW", Map.of());

      when(definitionRepository.existsByWorkspaceIdAndName(WORKSPACE_ID, "My Queue"))
          .thenReturn(false);
      when(definitionRepository.save(any())).thenAnswer(inv -> {
        WorkingQueueDefinitionEntity e = inv.getArgument(0);
        e.setId(QUEUE_ID);
        return e;
      });
      when(assignmentRepository.countByQueueAndStatus(eq(QUEUE_ID), any()))
          .thenReturn(0L);

      QueueSummaryResponse result = service.createQueue(WORKSPACE_ID, request);

      assertThat(result.name()).isEqualTo("My Queue");
    }

    @Test
    void should_throw_when_name_duplicate() {
      var request = new CreateQueueRequest("Existing", "PRICING_REVIEW", null);

      when(definitionRepository.existsByWorkspaceIdAndName(WORKSPACE_ID, "Existing"))
          .thenReturn(true);

      assertThatThrownBy(() -> service.createQueue(WORKSPACE_ID, request))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("updateQueue")
  class UpdateQueue {

    @Test
    void should_update_non_system_queue() {
      var entity = buildQueue(false);
      var request = new UpdateQueueRequest("New Name", Map.of(), true);

      when(definitionRepository.findByIdAndWorkspaceId(QUEUE_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(definitionRepository.existsByWorkspaceIdAndName(WORKSPACE_ID, "New Name"))
          .thenReturn(false);
      when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(assignmentRepository.countByQueueAndStatus(eq(QUEUE_ID), any()))
          .thenReturn(0L);

      service.updateQueue(WORKSPACE_ID, QUEUE_ID, request);

      assertThat(entity.getName()).isEqualTo("New Name");
    }

    @Test
    void should_throw_when_system_queue() {
      var entity = buildQueue(true);
      var request = new UpdateQueueRequest("Hack", null, true);

      when(definitionRepository.findByIdAndWorkspaceId(QUEUE_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.updateQueue(WORKSPACE_ID, QUEUE_ID, request))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("deleteQueue")
  class DeleteQueue {

    @Test
    void should_delete_non_system_queue() {
      var entity = buildQueue(false);
      when(definitionRepository.findByIdAndWorkspaceId(QUEUE_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.deleteQueue(WORKSPACE_ID, QUEUE_ID);

      verify(definitionRepository).delete(entity);
    }

    @Test
    void should_throw_when_system_queue() {
      var entity = buildQueue(true);
      when(definitionRepository.findByIdAndWorkspaceId(QUEUE_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.deleteQueue(WORKSPACE_ID, QUEUE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_when_queue_not_found() {
      when(definitionRepository.findByIdAndWorkspaceId(QUEUE_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deleteQueue(WORKSPACE_ID, QUEUE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("claimItem")
  class ClaimItem {

    @Test
    void should_claim_pending_item_via_cas() {
      mockQueueExists();
      when(assignmentRepository.casClaim(ITEM_ID, QUEUE_ID, USER_ID)).thenReturn(1);
      var item = buildItem(QueueAssignmentStatus.IN_PROGRESS);
      item.setAssignedToUserId(USER_ID);
      when(assignmentRepository.findByIdAndQueueDefinitionId(ITEM_ID, QUEUE_ID))
          .thenReturn(Optional.of(item));

      var result = service.claimItem(WORKSPACE_ID, QUEUE_ID, ITEM_ID, USER_ID);

      assertThat(result.status()).isEqualTo(QueueAssignmentStatus.IN_PROGRESS.name());
      verify(assignmentRepository).casClaim(ITEM_ID, QUEUE_ID, USER_ID);
    }

    @Test
    void should_throw_when_cas_claim_fails() {
      mockQueueExists();
      when(assignmentRepository.casClaim(ITEM_ID, QUEUE_ID, USER_ID)).thenReturn(0);

      assertThatThrownBy(() ->
          service.claimItem(WORKSPACE_ID, QUEUE_ID, ITEM_ID, USER_ID))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  @DisplayName("markDone")
  class MarkDone {

    @Test
    void should_mark_in_progress_item_as_done() {
      mockQueueExists();
      var item = buildItem(QueueAssignmentStatus.IN_PROGRESS);
      when(assignmentRepository.findByIdAndQueueDefinitionId(ITEM_ID, QUEUE_ID))
          .thenReturn(Optional.of(item));

      service.markDone(WORKSPACE_ID, QUEUE_ID, ITEM_ID, "Completed");

      assertThat(item.getStatus()).isEqualTo(QueueAssignmentStatus.DONE.name());
      assertThat(item.getNote()).isEqualTo("Completed");
    }

    @Test
    void should_throw_when_already_terminal() {
      mockQueueExists();
      var item = buildItem(QueueAssignmentStatus.DONE);
      when(assignmentRepository.findByIdAndQueueDefinitionId(ITEM_ID, QUEUE_ID))
          .thenReturn(Optional.of(item));

      assertThatThrownBy(() ->
          service.markDone(WORKSPACE_ID, QUEUE_ID, ITEM_ID, null))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("dismissItem")
  class DismissItem {

    @Test
    void should_dismiss_pending_item() {
      mockQueueExists();
      var item = buildItem(QueueAssignmentStatus.PENDING);
      when(assignmentRepository.findByIdAndQueueDefinitionId(ITEM_ID, QUEUE_ID))
          .thenReturn(Optional.of(item));

      service.dismissItem(WORKSPACE_ID, QUEUE_ID, ITEM_ID, "Not relevant");

      assertThat(item.getStatus()).isEqualTo(QueueAssignmentStatus.DISMISSED.name());
      assertThat(item.getNote()).isEqualTo("Not relevant");
    }

    @Test
    void should_throw_when_already_dismissed() {
      mockQueueExists();
      var item = buildItem(QueueAssignmentStatus.DISMISSED);
      when(assignmentRepository.findByIdAndQueueDefinitionId(ITEM_ID, QUEUE_ID))
          .thenReturn(Optional.of(item));

      assertThatThrownBy(() ->
          service.dismissItem(WORKSPACE_ID, QUEUE_ID, ITEM_ID, null))
          .isInstanceOf(BadRequestException.class);
    }
  }

  private WorkingQueueDefinitionEntity buildQueue(boolean isSystem) {
    var entity = new WorkingQueueDefinitionEntity();
    entity.setId(QUEUE_ID);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setName("Test Queue");
    entity.setQueueType("PRICING_REVIEW");
    entity.setEnabled(true);
    entity.setSystem(isSystem);
    return entity;
  }

  private WorkingQueueAssignmentEntity buildItem(QueueAssignmentStatus status) {
    var entity = new WorkingQueueAssignmentEntity();
    entity.setId(ITEM_ID);
    entity.setQueueDefinitionId(QUEUE_ID);
    entity.setEntityType("marketplace_offer");
    entity.setEntityId(1L);
    entity.setStatus(status.name());
    return entity;
  }

  private void mockQueueExists() {
    when(definitionRepository.findByIdAndWorkspaceId(QUEUE_ID, WORKSPACE_ID))
        .thenReturn(Optional.of(buildQueue(false)));
  }
}
