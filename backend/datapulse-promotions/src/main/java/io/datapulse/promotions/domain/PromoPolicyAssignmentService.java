package io.datapulse.promotions.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.CreatePromoAssignmentRequest;
import io.datapulse.promotions.api.PromoAssignmentResponse;
import io.datapulse.promotions.persistence.PromoEvaluationRunQueryRepository;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentEntity;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentReadRepository;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentRepository;
import io.datapulse.promotions.persistence.PromoPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoPolicyAssignmentService {

    private final PromoPolicyAssignmentRepository assignmentRepository;
    private final PromoPolicyAssignmentReadRepository assignmentReadRepository;
    private final PromoPolicyRepository policyRepository;
    private final PromoEvaluationRunQueryRepository runQueryRepository;

    @Transactional(readOnly = true)
    public List<PromoAssignmentResponse> listAssignments(long policyId, long workspaceId) {
        ensurePolicyExists(policyId, workspaceId);
        return assignmentReadRepository.findEnrichedByPolicyId(policyId);
    }

    @Transactional
    public PromoAssignmentResponse createAssignment(long policyId, CreatePromoAssignmentRequest request,
                                                    long workspaceId) {
        ensurePolicyExists(policyId, workspaceId);
        validateScopeConsistency(request);

        long connectionId = requireConnectionId(workspaceId, request.sourcePlatform());

        boolean exists = assignmentRepository.existsByPromoPolicyIdAndMarketplaceConnectionIdAndScopeType(
                policyId, connectionId, request.scopeType());
        if (exists && request.scopeType() == PromoScopeType.CONNECTION) {
            throw BadRequestException.of(MessageCodes.PROMO_ASSIGNMENT_DUPLICATE);
        }

        var entity = new PromoPolicyAssignmentEntity();
        entity.setPromoPolicyId(policyId);
        entity.setMarketplaceConnectionId(connectionId);
        entity.setScopeType(request.scopeType());
        entity.setCategoryId(request.categoryId());
        entity.setMarketplaceOfferId(request.marketplaceOfferId());

        PromoPolicyAssignmentEntity saved = assignmentRepository.save(entity);
        log.info("Promo assignment created: id={}, policyId={}, scopeType={}",
                saved.getId(), policyId, request.scopeType());

        return assignmentReadRepository.findEnrichedById(saved.getId());
    }

    @Transactional
    public void deleteAssignment(long policyId, long assignmentId, long workspaceId) {
        ensurePolicyExists(policyId, workspaceId);

        PromoPolicyAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.entity("PromoPolicyAssignment", assignmentId));

        if (!assignment.getPromoPolicyId().equals(policyId)) {
            throw NotFoundException.entity("PromoPolicyAssignment", assignmentId);
        }

        assignmentRepository.delete(assignment);
        log.info("Promo assignment deleted: id={}, policyId={}", assignmentId, policyId);
    }

    private long requireConnectionId(long workspaceId, String sourcePlatform) {
        Long connectionId = runQueryRepository.resolveConnectionId(workspaceId, sourcePlatform);
        if (connectionId == null) {
            throw NotFoundException.entity("MarketplaceConnection", sourcePlatform);
        }
        return connectionId;
    }

    private void ensurePolicyExists(long policyId, long workspaceId) {
        policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PromoPolicy", policyId));
    }

    private void validateScopeConsistency(CreatePromoAssignmentRequest request) {
        switch (request.scopeType()) {
            case SKU -> {
                if (request.marketplaceOfferId() == null) {
                    throw BadRequestException.of(MessageCodes.VALIDATION_FAILED, "marketplaceOfferId");
                }
            }
            case CATEGORY -> {
                if (request.categoryId() == null) {
                    throw BadRequestException.of(MessageCodes.VALIDATION_FAILED, "categoryId");
                }
            }
            case CONNECTION -> { /* no extra fields required */ }
        }
    }
}
