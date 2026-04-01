package io.datapulse.sellerops.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.CreateQueueRequest;
import io.datapulse.sellerops.api.QueueItemResponse;
import io.datapulse.sellerops.api.QueueSummaryResponse;
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

@Service
@RequiredArgsConstructor
public class WorkingQueueService {

    private final WorkingQueueDefinitionRepository definitionRepository;
    private final WorkingQueueAssignmentRepository assignmentRepository;

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
            throw BadRequestException.of(MessageCodes.DUPLICATE_ENTITY, "working_queue", request.name());
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

    @Transactional(readOnly = true)
    public Page<QueueItemResponse> listItems(long workspaceId, long queueId,
                                              String statusFilter, Pageable pageable) {
        WorkingQueueDefinitionEntity queue = findQueueOrThrow(workspaceId, queueId);

        Page<WorkingQueueAssignmentEntity> page;
        if (statusFilter != null) {
            page = assignmentRepository.findByQueueDefinitionIdAndStatusIn(
                    queue.getId(), List.of(statusFilter), pageable);
        } else {
            page = assignmentRepository.findByQueueDefinitionId(queue.getId(), pageable);
        }

        List<QueueItemResponse> items = page.getContent().stream()
                .map(this::toItemResponse)
                .toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    @Transactional
    public QueueItemResponse claimItem(long workspaceId, long queueId, long itemId, long userId) {
        findQueueOrThrow(workspaceId, queueId);
        WorkingQueueAssignmentEntity item = findItemOrThrow(queueId, itemId);

        if (!QueueAssignmentStatus.PENDING.name().equals(item.getStatus())) {
            throw BadRequestException.of(MessageCodes.INVALID_STATE, "queue_item");
        }

        item.setStatus(QueueAssignmentStatus.IN_PROGRESS.name());
        item.setAssignedToUserId(userId);
        assignmentRepository.save(item);
        return toItemResponse(item);
    }

    @Transactional
    public QueueItemResponse markDone(long workspaceId, long queueId, long itemId, String note) {
        findQueueOrThrow(workspaceId, queueId);
        WorkingQueueAssignmentEntity item = findItemOrThrow(queueId, itemId);

        if (QueueAssignmentStatus.valueOf(item.getStatus()).isTerminal()) {
            throw BadRequestException.of(MessageCodes.INVALID_STATE, "queue_item");
        }

        item.setStatus(QueueAssignmentStatus.DONE.name());
        if (note != null) {
            item.setNote(note);
        }
        assignmentRepository.save(item);
        return toItemResponse(item);
    }

    @Transactional
    public QueueItemResponse dismissItem(long workspaceId, long queueId, long itemId, String note) {
        findQueueOrThrow(workspaceId, queueId);
        WorkingQueueAssignmentEntity item = findItemOrThrow(queueId, itemId);

        if (QueueAssignmentStatus.valueOf(item.getStatus()).isTerminal()) {
            throw BadRequestException.of(MessageCodes.INVALID_STATE, "queue_item");
        }

        item.setStatus(QueueAssignmentStatus.DISMISSED.name());
        if (note != null) {
            item.setNote(note);
        }
        assignmentRepository.save(item);
        return toItemResponse(item);
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

    private QueueItemResponse toItemResponse(WorkingQueueAssignmentEntity entity) {
        return new QueueItemResponse(
                entity.getId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getStatus(),
                entity.getAssignedToUserId(),
                entity.getNote(),
                entity.getCreatedAt()
        );
    }
}
