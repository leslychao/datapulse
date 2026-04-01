package io.datapulse.pricing.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import io.datapulse.pricing.persistence.PricePolicyAssignmentEntity;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository.OfferRow;
import lombok.RequiredArgsConstructor;

/**
 * Resolves effective price policy for each offer based on assignment specificity + priority.
 * <p>
 * Resolution order:
 * 1. SKU-level (specificity=3) — exact match on marketplace_offer_id
 * 2. CATEGORY-level (specificity=2) — match on category_id
 * 3. CONNECTION-level (specificity=1) — fallback
 * <p>
 * Within same specificity: higher priority wins, then lower policy id.
 */
@Service
@RequiredArgsConstructor
public class PolicyResolver {

    private final PricePolicyRepository policyRepository;
    private final PricePolicyAssignmentRepository assignmentRepository;

    public Map<Long, PricePolicyEntity> resolveEffectivePolicies(long workspaceId, long connectionId,
                                                                 List<OfferRow> offers) {
        List<PricePolicyEntity> activePolicies = policyRepository
                .findAllByWorkspaceIdAndStatus(workspaceId, PolicyStatus.ACTIVE);
        if (activePolicies.isEmpty()) {
            return Map.of();
        }

        Map<Long, PricePolicyEntity> policyById = new HashMap<>();
        for (PricePolicyEntity policy : activePolicies) {
            policyById.put(policy.getId(), policy);
        }

        List<PricePolicyAssignmentEntity> assignments = assignmentRepository
                .findAllByMarketplaceConnectionId(connectionId);
        if (assignments.isEmpty()) {
            return Map.of();
        }

        List<PricePolicyAssignmentEntity> relevantAssignments = assignments.stream()
                .filter(a -> policyById.containsKey(a.getPricePolicyId()))
                .toList();

        Map<Long, PricePolicyEntity> result = new HashMap<>();

        for (OfferRow offer : offers) {
            PricePolicyEntity best = findBestPolicy(offer, relevantAssignments, policyById);
            if (best != null) {
                result.put(offer.id(), best);
            }
        }

        return result;
    }

    private PricePolicyEntity findBestPolicy(OfferRow offer,
                                             List<PricePolicyAssignmentEntity> assignments,
                                             Map<Long, PricePolicyEntity> policyById) {
        return assignments.stream()
                .filter(a -> matchesOffer(a, offer))
                .max(assignmentComparator(policyById))
                .map(a -> policyById.get(a.getPricePolicyId()))
                .orElse(null);
    }

    private boolean matchesOffer(PricePolicyAssignmentEntity assignment, OfferRow offer) {
        return switch (assignment.getScopeType()) {
            case SKU -> Objects.equals(assignment.getMarketplaceOfferId(), offer.id());
            case CATEGORY -> offer.categoryId() != null
                    && Objects.equals(assignment.getCategoryId(), offer.categoryId());
            case CONNECTION -> true;
        };
    }

    private Comparator<PricePolicyAssignmentEntity> assignmentComparator(
            Map<Long, PricePolicyEntity> policyById) {
        return Comparator
                .comparingInt((PricePolicyAssignmentEntity a) -> a.getScopeType().specificity())
                .thenComparing(a -> {
                    PricePolicyEntity policy = policyById.get(a.getPricePolicyId());
                    return policy != null ? policy.getPriority() : 0;
                })
                .thenComparing(PricePolicyAssignmentEntity::getPricePolicyId, Comparator.reverseOrder());
    }
}
