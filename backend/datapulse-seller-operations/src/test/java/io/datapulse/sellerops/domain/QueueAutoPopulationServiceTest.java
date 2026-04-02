package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.persistence.GridClickHouseReadRepository;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.WorkingQueueAssignmentEntity;
import io.datapulse.sellerops.persistence.WorkingQueueAssignmentRepository;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionEntity;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueAutoPopulationServiceTest {

  private static final long QUEUE_ID = 10L;
  private static final long WORKSPACE_ID = 1L;

  @Mock
  private WorkingQueueDefinitionRepository definitionRepository;
  @Mock
  private WorkingQueueAssignmentRepository assignmentRepository;
  @Mock
  private NamedParameterJdbcTemplate jdbc;
  @Mock
  private TransactionTemplate transactionTemplate;
  @Mock
  private GridClickHouseReadRepository chRepository;
  @Mock
  private GridPostgresReadRepository pgRepository;

  private QueueAutoPopulationService service;

  @BeforeEach
  void setUp() {
    doAnswer(invocation -> {
      invocation.getArgument(0, java.util.function.Consumer.class)
          .accept(null);
      return null;
    }).when(transactionTemplate).executeWithoutResult(any());

    service = new QueueAutoPopulationService(
        definitionRepository, assignmentRepository, jdbc,
        transactionTemplate, chRepository, pgRepository);
  }

  @Nested
  @DisplayName("populateAllQueues")
  class PopulateAllQueues {

    @Test
    void should_skip_queue_when_criteria_null() {
      var queue = buildQueue(null);
      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));

      service.populateAllQueues();

      verify(assignmentRepository, never()).save(any());
    }

    @Test
    void should_skip_queue_when_criteria_empty() {
      var queue = buildQueue(Map.of());
      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));

      service.populateAllQueues();

      verify(assignmentRepository, never()).save(any());
    }

    @Test
    void should_skip_queue_when_entity_type_missing() {
      var queue = buildQueue(Map.of(
          "match_rules", List.of(Map.of("field", "status", "op", "eq", "value", "FAILED"))));
      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));

      service.populateAllQueues();

      verify(assignmentRepository, never()).save(any());
    }

    @Test
    void should_add_new_items_when_criteria_match() {
      var queue = buildPriceActionQueue();

      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));
      when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenReturn(List.of(1L, 2L));
      when(assignmentRepository.findActiveAssignment(eq(QUEUE_ID), eq("price_action"), anyLong()))
          .thenReturn(Optional.empty());
      when(assignmentRepository.countActiveByQueue(QUEUE_ID)).thenReturn(0L);
      when(assignmentRepository.findAllActiveByQueue(QUEUE_ID))
          .thenReturn(List.of());

      service.populateAllQueues();

      ArgumentCaptor<WorkingQueueAssignmentEntity> captor =
          ArgumentCaptor.forClass(WorkingQueueAssignmentEntity.class);
      verify(assignmentRepository, times(2)).save(captor.capture());

      List<WorkingQueueAssignmentEntity> saved = captor.getAllValues();
      assertThat(saved).hasSize(2);
      assertThat(saved.get(0).getStatus()).isEqualTo(QueueAssignmentStatus.PENDING.name());
      assertThat(saved.get(0).getEntityType()).isEqualTo("price_action");
      assertThat(saved.get(0).getQueueDefinitionId()).isEqualTo(QUEUE_ID);
    }

    @Test
    void should_not_add_duplicate_items() {
      var queue = buildPriceActionQueue();

      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));
      when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenReturn(List.of(1L));

      var existing = new WorkingQueueAssignmentEntity();
      existing.setStatus(QueueAssignmentStatus.PENDING.name());
      when(assignmentRepository.findActiveAssignment(QUEUE_ID, "price_action", 1L))
          .thenReturn(Optional.of(existing));
      when(assignmentRepository.findAllActiveByQueue(QUEUE_ID))
          .thenReturn(List.of());

      service.populateAllQueues();

      verify(assignmentRepository, never()).save(any(WorkingQueueAssignmentEntity.class));
    }

    @Test
    void should_auto_resolve_stale_pending_assignments() {
      var queue = buildPriceActionQueue();

      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));
      when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenReturn(List.of(1L));
      when(assignmentRepository.findActiveAssignment(QUEUE_ID, "price_action", 1L))
          .thenReturn(Optional.empty());

      var staleItem = new WorkingQueueAssignmentEntity();
      staleItem.setEntityType("price_action");
      staleItem.setEntityId(999L);
      staleItem.setStatus(QueueAssignmentStatus.PENDING.name());
      when(assignmentRepository.findAllActiveByQueue(QUEUE_ID))
          .thenReturn(List.of(staleItem));

      service.populateAllQueues();

      assertThat(staleItem.getStatus()).isEqualTo(QueueAssignmentStatus.DONE.name());
      assertThat(staleItem.getNote()).contains("Auto-resolved");
    }

    @Test
    void should_not_auto_resolve_in_progress_items() {
      var queue = buildPriceActionQueue();

      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));
      when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenReturn(List.of());

      var inProgressItem = new WorkingQueueAssignmentEntity();
      inProgressItem.setEntityType("price_action");
      inProgressItem.setEntityId(999L);
      inProgressItem.setStatus(QueueAssignmentStatus.IN_PROGRESS.name());
      when(assignmentRepository.findAllActiveByQueue(QUEUE_ID))
          .thenReturn(List.of(inProgressItem));

      service.populateAllQueues();

      assertThat(inProgressItem.getStatus())
          .isEqualTo(QueueAssignmentStatus.IN_PROGRESS.name());
    }

    @Test
    void should_skip_unsupported_entity_type() {
      var queue = buildQueue(Map.of(
          "entity_type", "unknown_entity",
          "match_rules", List.of(
              Map.of("field", "status", "op", "eq", "value", "X"))));
      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));

      service.populateAllQueues();

      verify(assignmentRepository, never()).save(any());
    }

    @Test
    void should_continue_processing_next_queue_on_error() {
      var queue1 = buildPriceActionQueue();
      queue1.setId(1L);

      var queue2 = buildPriceActionQueue();
      queue2.setId(2L);

      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue1, queue2));

      when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenThrow(new RuntimeException("DB error"))
          .thenReturn(List.of());
      when(assignmentRepository.findAllActiveByQueue(2L))
          .thenReturn(List.of());

      service.populateAllQueues();

      verify(assignmentRepository).findAllActiveByQueue(2L);
    }

    @Test
    void should_auto_resolve_all_stale_when_no_matches() {
      var queue = buildPriceActionQueue();

      when(definitionRepository.findAllEnabledWithAutoCriteria())
          .thenReturn(List.of(queue));
      when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenReturn(List.of());

      var staleItem = new WorkingQueueAssignmentEntity();
      staleItem.setEntityType("price_action");
      staleItem.setEntityId(42L);
      staleItem.setStatus(QueueAssignmentStatus.PENDING.name());
      when(assignmentRepository.findAllActiveByQueue(QUEUE_ID))
          .thenReturn(List.of(staleItem));

      service.populateAllQueues();

      assertThat(staleItem.getStatus()).isEqualTo(QueueAssignmentStatus.DONE.name());
    }
  }

  private WorkingQueueDefinitionEntity buildQueue(Map<String, Object> autoCriteria) {
    var entity = new WorkingQueueDefinitionEntity();
    entity.setId(QUEUE_ID);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setName("Auto Queue");
    entity.setQueueType("PRICING_REVIEW");
    entity.setEnabled(true);
    entity.setAutoCriteria(autoCriteria);
    return entity;
  }

  private WorkingQueueDefinitionEntity buildPriceActionQueue() {
    return buildQueue(Map.of(
        "entity_type", "price_action",
        "match_rules", List.of(
            Map.of("field", "status", "op", "eq", "value", "FAILED"))));
  }
}
