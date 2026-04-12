package io.datapulse.pricing.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.AssignmentResponse;
import io.datapulse.pricing.api.CategorySuggestionResponse;
import io.datapulse.pricing.api.CreateAssignmentRequest;
import io.datapulse.pricing.api.OfferSuggestionResponse;
import io.datapulse.pricing.persistence.AssignmentSuggestionReadRepository;
import io.datapulse.pricing.persistence.PricePolicyAssignmentEntity;
import io.datapulse.pricing.persistence.PricePolicyAssignmentReadRepository;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import io.datapulse.pricing.persistence.PricingRunReadRepository;
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
  private final PricePolicyAssignmentReadRepository assignmentReadRepository;
  private final AssignmentSuggestionReadRepository suggestionReadRepository;
  private final PricePolicyRepository policyRepository;
  private final PricingRunReadRepository runReadRepository;

  @Transactional(readOnly = true)
  public List<AssignmentResponse> listAssignments(long policyId, long workspaceId) {
    ensurePolicyExists(policyId, workspaceId);
    return assignmentReadRepository.findEnrichedByPolicyId(policyId);
  }

  @Transactional
  public AssignmentResponse createAssignment(long policyId, CreateAssignmentRequest request,
                                             long workspaceId) {
    ensurePolicyExists(policyId, workspaceId);
    long connectionId = requireConnectionId(workspaceId, request.sourcePlatform());
    validateScopeConsistency(request);
    checkDuplicate(policyId, connectionId, request);

    var entity = new PricePolicyAssignmentEntity();
    entity.setPricePolicyId(policyId);
    entity.setMarketplaceConnectionId(connectionId);
    entity.setScopeType(request.scopeType());
    entity.setCategoryId(request.categoryId());
    entity.setMarketplaceOfferId(request.marketplaceOfferId());

    PricePolicyAssignmentEntity saved = assignmentRepository.save(entity);
    log.info("Assignment created: id={}, policyId={}, scopeType={}",
        saved.getId(), policyId, request.scopeType());

    return assignmentReadRepository.findEnrichedById(saved.getId());
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

  @Transactional(readOnly = true)
  public List<CategorySuggestionResponse> listCategories(
      long workspaceId, String sourcePlatform, String search) {
    long connectionId = requireConnectionId(workspaceId, sourcePlatform);
    return suggestionReadRepository.findCategories(connectionId, search);
  }

  @Transactional(readOnly = true)
  public List<OfferSuggestionResponse> searchOffers(
      long workspaceId, String sourcePlatform, String search) {
    long connectionId = requireConnectionId(workspaceId, sourcePlatform);
    return suggestionReadRepository.searchOffers(connectionId, search);
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

  private long requireConnectionId(long workspaceId, String sourcePlatform) {
    Long connectionId = runReadRepository.resolveConnectionId(workspaceId, sourcePlatform);
    if (connectionId == null) {
      throw NotFoundException.entity("MarketplaceConnection", sourcePlatform);
    }
    return connectionId;
  }

  private void checkDuplicate(long policyId, long connectionId,
                               CreateAssignmentRequest request) {
    boolean exists = switch (request.scopeType()) {
      case CONNECTION -> assignmentRepository
          .existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeType(
              policyId, connectionId, ScopeType.CONNECTION);
      case CATEGORY -> assignmentRepository
          .existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndCategoryId(
              policyId, connectionId, ScopeType.CATEGORY, request.categoryId());
      case SKU -> assignmentRepository
          .existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndMarketplaceOfferId(
              policyId, connectionId, ScopeType.SKU, request.marketplaceOfferId());
    };
    if (exists) {
      throw BadRequestException.of(MessageCodes.PRICING_ASSIGNMENT_DUPLICATE);
    }
  }
}
