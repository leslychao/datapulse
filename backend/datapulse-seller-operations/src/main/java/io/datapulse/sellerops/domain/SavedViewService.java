package io.datapulse.sellerops.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.CreateSavedViewRequest;
import io.datapulse.sellerops.api.SavedViewSummaryResponse;
import io.datapulse.sellerops.api.UpdateSavedViewRequest;
import io.datapulse.sellerops.persistence.SavedViewEntity;
import io.datapulse.sellerops.persistence.SavedViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedViewService {

    private final SavedViewRepository repository;

    @Transactional(readOnly = true)
    public List<SavedViewSummaryResponse> listViews(long workspaceId, long userId) {
        return repository.findByWorkspaceIdIncludingSystem(workspaceId, userId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private static final int MAX_VIEWS_PER_USER = 50;

    @Transactional
    public SavedViewSummaryResponse createView(long workspaceId, long userId,
                                                CreateSavedViewRequest request) {
        if (repository.existsByWorkspaceIdAndUserIdAndName(workspaceId, userId, request.name())) {
            throw BadRequestException.of(MessageCodes.DUPLICATE_ENTITY, "saved_view", request.name());
        }

        long currentCount = repository.countByWorkspaceIdAndUserId(workspaceId, userId);
        if (currentCount >= MAX_VIEWS_PER_USER) {
            throw BadRequestException.of(MessageCodes.SAVED_VIEW_LIMIT_EXCEEDED, MAX_VIEWS_PER_USER);
        }

        var entity = new SavedViewEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setName(request.name());
        entity.setDefault(request.isDefault());
        entity.setSystem(false);
        entity.setFilters(request.filters());
        entity.setSortColumn(request.sortColumn());
        entity.setSortDirection(request.sortDirection());
        entity.setVisibleColumns(request.visibleColumns());
        entity.setGroupBySku(request.groupBySku());

        if (request.isDefault()) {
            clearDefaultFlag(workspaceId, userId);
        }

        SavedViewEntity saved = repository.save(entity);
        return toSummary(saved);
    }

    @Transactional
    public SavedViewSummaryResponse updateView(long workspaceId, long userId, long viewId,
                                                UpdateSavedViewRequest request) {
        SavedViewEntity entity = repository.findByIdAndWorkspaceId(viewId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("saved_view", viewId));

        if (entity.isSystem()) {
            throw BadRequestException.of(MessageCodes.OPERATION_NOT_ALLOWED, "saved_view");
        }

        if (!entity.getUserId().equals(userId)) {
            throw BadRequestException.of(MessageCodes.OPERATION_NOT_ALLOWED, "saved_view");
        }

        entity.setName(request.name());
        entity.setDefault(request.isDefault());
        entity.setFilters(request.filters());
        entity.setSortColumn(request.sortColumn());
        entity.setSortDirection(request.sortDirection());
        entity.setVisibleColumns(request.visibleColumns());
        entity.setGroupBySku(request.groupBySku());

        if (request.isDefault()) {
            clearDefaultFlag(workspaceId, userId);
        }

        SavedViewEntity saved = repository.save(entity);
        return toSummary(saved);
    }

    @Transactional
    public void deleteView(long workspaceId, long userId, long viewId) {
        SavedViewEntity entity = repository.findByIdAndWorkspaceId(viewId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("saved_view", viewId));

        if (entity.isSystem()) {
            throw BadRequestException.of(MessageCodes.OPERATION_NOT_ALLOWED, "saved_view");
        }

        if (!entity.getUserId().equals(userId)) {
            throw BadRequestException.of(MessageCodes.OPERATION_NOT_ALLOWED, "saved_view");
        }

        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public SavedViewEntity findById(long workspaceId, long viewId) {
        return repository.findByIdAndWorkspaceId(viewId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("saved_view", viewId));
    }

    private void clearDefaultFlag(long workspaceId, long userId) {
        repository.findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(workspaceId, userId)
                .stream()
                .filter(SavedViewEntity::isDefault)
                .forEach(v -> {
                    v.setDefault(false);
                    repository.save(v);
                });
    }

    private SavedViewSummaryResponse toSummary(SavedViewEntity entity) {
        return new SavedViewSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.isDefault(),
                entity.isSystem(),
                entity.getCreatedAt()
        );
    }
}
