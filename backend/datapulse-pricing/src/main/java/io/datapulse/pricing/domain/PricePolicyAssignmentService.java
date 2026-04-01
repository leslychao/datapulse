package io.datapulse.pricing.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.AssignmentMapper;
import io.datapulse.pricing.api.AssignmentResponse;
import io.datapulse.pricing.api.CreateAssignmentRequest;
import io.datapulse.pricing.persistence.PricePolicyAssignmentEntity;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricePolicyAssignmentService {

    private final PricePolicyAssignmentRepository assignmentRepository;
    private final PricePolicyRepository policyRepository;
    private final AssignmentMapper assignmentMapper;

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listAssignments(long policyId, long workspaceId) {
        ensurePolicyExists(policyId, workspaceId);
        List<PricePolicyAssignmentEntity> assignments = assignmentRepository.findAllByPricePolicyId(policyId);
        return assignmentMapper.toResponses(assignments);
    }

    @Transactional
    public AssignmentResponse createAssignment(long policyId, CreateAssignmentRequest request,
                                               long workspaceId) {
        ensurePolicyExists(policyId, workspaceId);

        validateScopeConsistency(request);

        boolean exists = assignmentRepository.existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeType(
                policyId, request.connectionId(), request.scopeType());
        if (exists && request.scopeType() == ScopeType.CONNECTION) {
            throw BadRequestException.of(MessageCodes.PRICING_ASSIGNMENT_DUPLICATE);
        }

        var entity = new PricePolicyAssignmentEntity();
        entity.setPricePolicyId(policyId);
        entity.setMarketplaceConnectionId(request.connectionId());
        entity.setScopeType(request.scopeType());
        entity.setCategoryId(request.categoryId());
        entity.setMarketplaceOfferId(request.marketplaceOfferId());

        PricePolicyAssignmentEntity saved = assignmentRepository.save(entity);
        log.info("Assignment created: id={}, policyId={}, scopeType={}",
                saved.getId(), policyId, request.scopeType());

        return assignmentMapper.toResponse(saved);
    }

    @Transactional
    public void deleteAssignment(long policyId, long assignmentId, long workspaceId) {
        ensurePolicyExists(policyId, workspaceId);

        PricePolicyAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.entity("PricePolicyAssignment", assignmentId));

        if (!assignment.getPricePolicyId().equals(policyId)) {
            throw NotFoundException.entity("PricePolicyAssignment", assignmentId);
        }

        assignmentRepository.delete(assignment);
        log.info("Assignment deleted: id={}, policyId={}", assignmentId, policyId);
    }

    private void ensurePolicyExists(long policyId, long workspaceId) {
        policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PricePolicy", policyId));
    }

    private void validateScopeConsistency(CreateAssignmentRequest request) {
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
