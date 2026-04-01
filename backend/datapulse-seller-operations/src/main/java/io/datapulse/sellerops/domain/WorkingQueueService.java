package io.datapulse.sellerops.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.CreateQueueRequest;
import io.datapulse.sellerops.api.PreviewCountRequest;
import io.datapulse.sellerops.api.PreviewCountResponse;
import io.datapulse.sellerops.api.QueueItemResponse;
import io.datapulse.sellerops.api.QueueSummaryResponse;
import io.datapulse.sellerops.api.UpdateQueueRequest;
import io.datapulse.sellerops.persistence.QueueItemSummaryJdbcRepository;
import io.datapulse.sellerops.persistence.WorkingQueueAssignmentEntity;
import io.datapulse.sellerops.persistence.WorkingQueueAssignmentRepository;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionEntity;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkingQueueService {

  private final WorkingQueueDefinitionRepository definitionRepository;
  private final WorkingQueueAssignmentRepository assignmentRepository;
  private final QueueItemSummaryJdbcRepository summaryRepository;
  private final QueueAutoPopulationService autoPopulationService;

  @Transactional(readOnly = true)
  public QueueSummaryResponse getQueue(long workspaceId, long queueId) {
    return toQueueSummary(findQueueOrThrow(workspaceId, queueId));
  }

  @Transactional(readOnly = true)
  public PreviewCountResponse previewCount(long workspaceId, PreviewCountRequest request) {
    long count = autoPopulationService.countByCriteria(workspaceId, request.autoCriteria());
    return new PreviewCountResponse(count);
  }

  @Transactional(readOnly = true)
  public List<QueueSummaryResponse> listQueues(long workspaceId) {
    return definitionRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId)
        .stream()
        .map(this::toQueueSummary)
        .toList();
  }

  @Transactional
  public QueueSummaryResponse createQueue(long workspaceId, CreateQueueRequest request) {
    if (definitionRepository.existsByWorkspaceIdAndName(workspaceId, request.name())) {
      throw BadRequestException.of(
          MessageCodes.DUPLICATE_ENTITY, "working_queue", request.name());
    }

    var entity = new WorkingQueueDefinitionEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setName(request.name());
    entity.setQueueType(request.queueType());
    entity.setAutoCriteria(request.autoCriteria());
    entity.setEnabled(true);

    WorkingQueueDefinitionEntity saved = definitionRepository.save(entity);
    return toQueueSummary(saved);
  }

  @Transactional
  public QueueSummaryResponse updateQueue(long workspaceId, long queueId,
                                           UpdateQueueRequest request) {
    WorkingQueueDefinitionEntity entity = findQueueOrThrow(workspaceId, queueId);
    ensureNotSystem(entity);

    if (!entity.getName().equals(request.name())
        && definitionRepository.existsByWorkspaceIdAndName(workspaceId, request.name())) {
      throw BadRequestException.of(
          MessageCodes.DUPLICATE_ENTITY, "working_queue", request.name());
    }

    entity.setName(request.name());
    entity.setAutoCriteria(request.autoCriteria());
    entity.setEnabled(request.enabled());

    WorkingQueueDefinitionEntity saved = definitionRepository.save(entity);
    return toQueueSummary(saved);
  }

  @Transactional
  public void deleteQueue(long workspaceId, long queueId) {
    WorkingQueueDefinitionEntity entity = findQueueOrThrow(workspaceId, queueId);
    ensureNotSystem(entity);
    definitionRepository.delete(entity);
  }

  private void ensureNotSystem(WorkingQueueDefinitionEntity entity) {
    if (entity.isSystem()) {
      throw BadRequestException.of(MessageCodes.QUEUE_SYSTEM_IMMUTABLE);
    }
  }

  @Transactional(readOnly = true)
  public Page<QueueItemResponse> listItems(long workspaceId, long queueId,
                                            String statusFilter, Long assignedToUserId,
                                            Pageable pageable) {
    WorkingQueueDefinitionEntity queue = findQueueOrThrow(workspaceId, queueId);

    Page<WorkingQueueAssignmentEntity> page = resolveItemsPage(
        queue.getId(), statusFilter, assignedToUserId, pageable);

    return enrichWithSummary(page, pageable);
  }

  @Transactional
  public QueueItemResponse claimItem(long workspaceId, long queueId,
                                      long itemId, long userId) {
    findQueueOrThrow(workspaceId, queueId);
    WorkingQueueAssignmentEntity item = findItemOrThrow(queueId, itemId);

    if (!QueueAssignmentStatus.PENDING.name().equals(item.getStatus())) {
      throw BadRequestException.of(MessageCodes.QUEUE_ITEM_INVALID_STATE, "queue_item");
    }

    item.setStatus(QueueAssignmentStatus.IN_PROGRESS.name());
    item.setAssignedToUserId(userId);
    assignmentRepository.save(item);
    return toItemResponse(item, null);
  }

  @Transactional
  public QueueItemResponse markDone(long workspaceId, long queueId,
                                     long itemId, String note) {
    findQueueOrThrow(workspaceId, queueId);
    WorkingQueueAssignmentEntity item = findItemOrThrow(queueId, itemId);

    if (QueueAssignmentStatus.valueOf(item.getStatus()).isTerminal()) {
      throw BadRequestException.of(MessageCodes.QUEUE_ITEM_INVALID_STATE, "queue_item");
    }

    item.setStatus(QueueAssignmentStatus.DONE.name());
    if (note != null) {
      item.setNote(note);
    }
    assignmentRepository.save(item);
    return toItemResponse(item, null);
  }

  @Transactional
  public QueueItemResponse dismissItem(long workspaceId, long queueId,
                                        long itemId, String note) {
    findQueueOrThrow(workspaceId, queueId);
    WorkingQueueAssignmentEntity item = findItemOrThrow(queueId, itemId);

    if (QueueAssignmentStatus.valueOf(item.getStatus()).isTerminal()) {
      throw BadRequestException.of(MessageCodes.QUEUE_ITEM_INVALID_STATE, "queue_item");
    }

    item.setStatus(QueueAssignmentStatus.DISMISSED.name());
    if (note != null) {
      item.setNote(note);
    }
    assignmentRepository.save(item);
    return toItemResponse(item, null);
  }

  private Page<WorkingQueueAssignmentEntity> resolveItemsPage(
      long queueId, String statusFilter, Long assignedToUserId,
      Pageable pageable) {
    boolean hasStatus = statusFilter != null;
    boolean hasUser = assignedToUserId != null;

    if (hasStatus && hasUser) {
      return assignmentRepository.findByQueueDefinitionIdAndStatusInAndAssignedToUserId(
          queueId, List.of(statusFilter), assignedToUserId, pageable);
    }
    if (hasStatus) {
      return assignmentRepository.findByQueueDefinitionIdAndStatusIn(
          queueId, List.of(statusFilter), pageable);
    }
    if (hasUser) {
      return assignmentRepository.findByQueueDefinitionIdAndAssignedToUserId(
          queueId, assignedToUserId, pageable);
    }
    return assignmentRepository.findByQueueDefinitionId(queueId, pageable);
  }

  private Page<QueueItemResponse> enrichWithSummary(
      Page<WorkingQueueAssignmentEntity> page, Pageable pageable) {
    if (page.isEmpty()) {
      return Page.empty(pageable);
    }

    List<WorkingQueueAssignmentEntity> items = page.getContent();
    String entityType = items.get(0).getEntityType();
    List<Long> entityIds = items.stream()
        .map(WorkingQueueAssignmentEntity::getEntityId)
        .toList();

    Map<Long, Map<String, Object>> summaries =
        summaryRepository.fetchSummaries(entityType, entityIds);

    List<QueueItemResponse> enriched = items.stream()
        .map(item -> toItemResponse(item, summaries.get(item.getEntityId())))
        .toList();

    return new PageImpl<>(enriched, pageable, page.getTotalElements());
  }

  private WorkingQueueDefinitionEntity findQueueOrThrow(long workspaceId, long queueId) {
    return definitionRepository.findByIdAndWorkspaceId(queueId, workspaceId)
        .orElseThrow(() -> NotFoundException.entity("working_queue", queueId));
  }

  private WorkingQueueAssignmentEntity findItemOrThrow(long queueId, long itemId) {
    return assignmentRepository.findByIdAndQueueDefinitionId(itemId, queueId)
        .orElseThrow(() -> NotFoundException.entity("queue_item", itemId));
  }

  private QueueSummaryResponse toQueueSummary(WorkingQueueDefinitionEntity entity) {
    long pendingCount = assignmentRepository.countByQueueAndStatus(
        entity.getId(), QueueAssignmentStatus.PENDING.name());
    long inProgressCount = assignmentRepository.countByQueueAndStatus(
        entity.getId(), QueueAssignmentStatus.IN_PROGRESS.name());

    return new QueueSummaryResponse(
        entity.getId(),
        entity.getName(),
        entity.getQueueType(),
        pendingCount,
        inProgressCount,
        pendingCount + inProgressCount
    );
  }

  private QueueItemResponse toItemResponse(WorkingQueueAssignmentEntity entity,
                                            Map<String, Object> entitySummary) {
    return new QueueItemResponse(
        entity.getId(),
        entity.getEntityType(),
        entity.getEntityId(),
        entity.getStatus(),
        entity.getAssignedToUserId(),
        entity.getNote(),
        entity.getCreatedAt(),
        entitySummary
    );
  }
}
