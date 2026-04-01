package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.pricing.persistence.PricePolicyAssignmentEntity;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository.OfferRow;

@ExtendWith(MockitoExtension.class)
class PolicyResolverTest {

  @Mock private PricePolicyRepository policyRepository;
  @Mock private PricePolicyAssignmentRepository assignmentRepository;

  @InjectMocks
  private PolicyResolver resolver;

  private static final long WORKSPACE_ID = 10L;
  private static final long CONNECTION_ID = 20L;

  private PricePolicyEntity policyA;
  private PricePolicyEntity policyB;
  private PricePolicyEntity policyC;

  @BeforeEach
  void setUp() {
    policyA = buildPolicy(1L, "Policy A", 0);
    policyB = buildPolicy(2L, "Policy B", 0);
    policyC = buildPolicy(3L, "Policy C", 10);
  }

  @Nested
  @DisplayName("empty results")
  class EmptyResults {

    @Test
    @DisplayName("returns empty map when no active policies exist")
    void should_returnEmpty_when_noActivePolicies() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of());

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty map when no assignments exist for connection")
    void should_returnEmpty_when_noAssignments() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA));
      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of());

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("skips offer without matching assignment")
    void should_skipOffer_when_noMatchingAssignment() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA));

      PricePolicyAssignmentEntity assignment = buildAssignment(
          policyA.getId(), ScopeType.SKU, null, 999L);
      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(assignment));

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).doesNotContainKey(100L);
    }
  }

  @Nested
  @DisplayName("specificity precedence: SKU > CATEGORY > CONNECTION")
  class SpecificityPrecedence {

    @Test
    @DisplayName("SKU assignment wins over CATEGORY and CONNECTION")
    void should_resolveSku_when_allThreePresent() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA, policyB, policyC));

      PricePolicyAssignmentEntity connAssign = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);
      PricePolicyAssignmentEntity catAssign = buildAssignment(
          policyB.getId(), ScopeType.CATEGORY, 5L, null);
      PricePolicyAssignmentEntity skuAssign = buildAssignment(
          policyC.getId(), ScopeType.SKU, null, 100L);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssign, catAssign, skuAssign));

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).containsEntry(100L, policyC);
    }

    @Test
    @DisplayName("CATEGORY wins over CONNECTION when no SKU match")
    void should_resolveCategory_when_noSkuMatch() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA, policyB));

      PricePolicyAssignmentEntity connAssign = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);
      PricePolicyAssignmentEntity catAssign = buildAssignment(
          policyB.getId(), ScopeType.CATEGORY, 5L, null);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssign, catAssign));

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).containsEntry(100L, policyB);
    }

    @Test
    @DisplayName("CONNECTION is fallback when no SKU or CATEGORY match")
    void should_resolveConnection_when_noSpecificMatch() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA));

      PricePolicyAssignmentEntity connAssign = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssign));

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).containsEntry(100L, policyA);
    }

    @Test
    @DisplayName("CATEGORY assignment does not match offer with null categoryId")
    void should_skipCategory_when_offerCategoryNull() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA, policyB));

      PricePolicyAssignmentEntity connAssign = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);
      PricePolicyAssignmentEntity catAssign = buildAssignment(
          policyB.getId(), ScopeType.CATEGORY, 5L, null);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssign, catAssign));

      OfferRow offer = new OfferRow(100L, 1L, null, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).containsEntry(100L, policyA);
    }
  }

  @Nested
  @DisplayName("priority and tiebreaker")
  class PriorityTiebreaker {

    @Test
    @DisplayName("higher priority wins within same specificity")
    void should_resolveHigherPriority_when_sameSpecificity() {
      policyA.setPriority(5);
      policyB.setPriority(10);

      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA, policyB));

      PricePolicyAssignmentEntity assign1 = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);
      PricePolicyAssignmentEntity assign2 = buildAssignment(
          policyB.getId(), ScopeType.CONNECTION, null, null);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(assign1, assign2));

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).containsEntry(100L, policyB);
    }

    @Test
    @DisplayName("lower policy id wins when priority and specificity are equal")
    void should_resolveLowerId_when_samePriorityAndSpecificity() {
      policyA.setPriority(5);
      policyB.setPriority(5);

      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA, policyB));

      PricePolicyAssignmentEntity assign1 = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);
      PricePolicyAssignmentEntity assign2 = buildAssignment(
          policyB.getId(), ScopeType.CONNECTION, null, null);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(assign1, assign2));

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).containsEntry(100L, policyA);
    }
  }

  @Nested
  @DisplayName("multiple offers")
  class MultipleOffers {

    @Test
    @DisplayName("resolves different policies for different offers")
    void should_resolveDifferentPolicies_when_multipleOffers() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA, policyB));

      PricePolicyAssignmentEntity connAssign = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);
      PricePolicyAssignmentEntity skuAssign = buildAssignment(
          policyB.getId(), ScopeType.SKU, null, 200L);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(connAssign, skuAssign));

      OfferRow offer1 = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      OfferRow offer2 = new OfferRow(200L, 2L, 5L, CONNECTION_ID, "ACTIVE");

      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer1, offer2));

      assertThat(result).containsEntry(100L, policyA);
      assertThat(result).containsEntry(200L, policyB);
    }
  }

  @Nested
  @DisplayName("inactive policy filtering")
  class InactivePolicyFiltering {

    @Test
    @DisplayName("ignores assignment when its policy is not in active list")
    void should_ignoreAssignment_when_policyNotActive() {
      PricePolicyEntity inactivePolicy = buildPolicy(50L, "Inactive", 0);

      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of(policyA));

      PricePolicyAssignmentEntity activeAssign = buildAssignment(
          policyA.getId(), ScopeType.CONNECTION, null, null);
      PricePolicyAssignmentEntity inactiveAssign = buildAssignment(
          inactivePolicy.getId(), ScopeType.SKU, null, 100L);

      when(assignmentRepository.findAllByMarketplaceConnectionId(CONNECTION_ID))
          .thenReturn(List.of(activeAssign, inactiveAssign));

      OfferRow offer = new OfferRow(100L, 1L, 5L, CONNECTION_ID, "ACTIVE");
      Map<Long, PricePolicyEntity> result = resolver.resolveEffectivePolicies(
          WORKSPACE_ID, CONNECTION_ID, List.of(offer));

      assertThat(result).containsEntry(100L, policyA);
    }
  }

  private PricePolicyEntity buildPolicy(long id, String name, int priority) {
    var policy = new PricePolicyEntity();
    policy.setId(id);
    policy.setWorkspaceId(WORKSPACE_ID);
    policy.setName(name);
    policy.setStatus(PolicyStatus.ACTIVE);
    policy.setStrategyType(PolicyType.TARGET_MARGIN);
    policy.setStrategyParams("{}");
    policy.setExecutionMode(ExecutionMode.RECOMMENDATION);
    policy.setVersion(1);
    policy.setPriority(priority);
    return policy;
  }

  private PricePolicyAssignmentEntity buildAssignment(long policyId, ScopeType scopeType,
                                                      Long categoryId, Long offerId) {
    var assignment = new PricePolicyAssignmentEntity();
    assignment.setPricePolicyId(policyId);
    assignment.setMarketplaceConnectionId(CONNECTION_ID);
    assignment.setScopeType(scopeType);
    assignment.setCategoryId(categoryId);
    assignment.setMarketplaceOfferId(offerId);
    return assignment;
  }
}
