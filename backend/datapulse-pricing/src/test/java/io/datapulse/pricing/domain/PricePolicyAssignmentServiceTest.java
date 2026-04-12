package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.AssignmentResponse;
import io.datapulse.pricing.api.CreateAssignmentRequest;
import io.datapulse.pricing.persistence.AssignmentSuggestionReadRepository;
import io.datapulse.pricing.persistence.PricePolicyAssignmentEntity;
import io.datapulse.pricing.persistence.PricePolicyAssignmentReadRepository;
import io.datapulse.pricing.persistence.PricePolicyAssignmentRepository;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricePolicyRepository;

@ExtendWith(MockitoExtension.class)
class PricePolicyAssignmentServiceTest {

  @Mock private PricePolicyAssignmentRepository assignmentRepository;
  @Mock private PricePolicyAssignmentReadRepository assignmentReadRepository;
  @Mock private AssignmentSuggestionReadRepository suggestionReadRepository;
  @Mock private PricePolicyRepository policyRepository;

  @InjectMocks
  private PricePolicyAssignmentService service;

  @Captor
  private ArgumentCaptor<PricePolicyAssignmentEntity> entityCaptor;

  private static final long WORKSPACE_ID = 10L;
  private static final long POLICY_ID = 1L;
  private static final long CONNECTION_ID = 20L;

  @Nested
  @DisplayName("listAssignments")
  class ListAssignments {

    @Test
    @DisplayName("returns enriched assignments when policy exists")
    void should_returnAssignments_when_policyExists() {
      mockPolicyExists();
      when(assignmentReadRepository.findEnrichedByPolicyId(POLICY_ID))
          .thenReturn(List.of());

      service.listAssignments(POLICY_ID, WORKSPACE_ID);

      verify(assignmentReadRepository).findEnrichedByPolicyId(POLICY_ID);
    }

    @Test
    @DisplayName("throws NotFoundException when policy does not exist")
    void should_throwNotFound_when_policyNotExists() {
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.listAssignments(POLICY_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("createAssignment")
  class CreateAssignment {

    @Test
    @DisplayName("creates CONNECTION assignment successfully")
    void should_create_when_connectionScope() {
      mockPolicyExists();
      when(assignmentRepository.existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeType(
          POLICY_ID, CONNECTION_ID, ScopeType.CONNECTION)).thenReturn(false);
      when(assignmentRepository.save(any())).thenAnswer(i -> {
        PricePolicyAssignmentEntity e = i.getArgument(0);
        e.setId(1L);
        return e;
      });
      when(assignmentReadRepository.findEnrichedById(1L)).thenReturn(
          new AssignmentResponse(1L, POLICY_ID,
              "Test Connection", "WB", ScopeType.CONNECTION,
              null, null, null, null, null));

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.CONNECTION, null, null);
      AssignmentResponse response = service.createAssignment(POLICY_ID, request, WORKSPACE_ID);

      verify(assignmentRepository).save(entityCaptor.capture());
      PricePolicyAssignmentEntity saved = entityCaptor.getValue();
      assertThat(saved.getScopeType()).isEqualTo(ScopeType.CONNECTION);
      assertThat(saved.getPricePolicyId()).isEqualTo(POLICY_ID);
      assertThat(response.connectionName()).isEqualTo("Test Connection");
    }

    @Test
    @DisplayName("rejects duplicate CONNECTION assignment")
    void should_throwBadRequest_when_duplicateConnectionAssignment() {
      mockPolicyExists();
      when(assignmentRepository.existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeType(
          POLICY_ID, CONNECTION_ID, ScopeType.CONNECTION)).thenReturn(true);

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.CONNECTION, null, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects duplicate CATEGORY assignment")
    void should_throwBadRequest_when_duplicateCategoryAssignment() {
      mockPolicyExists();
      when(assignmentRepository.existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndCategoryId(
          POLICY_ID, CONNECTION_ID, ScopeType.CATEGORY, 5L)).thenReturn(true);

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.CATEGORY, 5L, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects duplicate SKU assignment")
    void should_throwBadRequest_when_duplicateSkuAssignment() {
      mockPolicyExists();
      when(assignmentRepository.existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndMarketplaceOfferId(
          POLICY_ID, CONNECTION_ID, ScopeType.SKU, 100L)).thenReturn(true);

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.SKU, null, 100L);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects SKU assignment without marketplaceOfferId")
    void should_throwBadRequest_when_skuWithoutOfferId() {
      mockPolicyExists();

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.SKU, null, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects CATEGORY assignment without categoryId")
    void should_throwBadRequest_when_categoryWithoutCategoryId() {
      mockPolicyExists();

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.CATEGORY, null, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("creates SKU assignment with offer id")
    void should_create_when_skuWithOfferId() {
      mockPolicyExists();
      when(assignmentRepository.existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndMarketplaceOfferId(
          POLICY_ID, CONNECTION_ID, ScopeType.SKU, 100L)).thenReturn(false);
      when(assignmentRepository.save(any())).thenAnswer(i -> {
        PricePolicyAssignmentEntity e = i.getArgument(0);
        e.setId(2L);
        return e;
      });
      when(assignmentReadRepository.findEnrichedById(2L)).thenReturn(null);

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.SKU, null, 100L);
      service.createAssignment(POLICY_ID, request, WORKSPACE_ID);

      verify(assignmentRepository).save(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getMarketplaceOfferId()).isEqualTo(100L);
      assertThat(entityCaptor.getValue().getScopeType()).isEqualTo(ScopeType.SKU);
    }

    @Test
    @DisplayName("creates CATEGORY assignment with category id")
    void should_create_when_categoryWithCategoryId() {
      mockPolicyExists();
      when(assignmentRepository.existsByPricePolicyIdAndMarketplaceConnectionIdAndScopeTypeAndCategoryId(
          POLICY_ID, CONNECTION_ID, ScopeType.CATEGORY, 5L)).thenReturn(false);
      when(assignmentRepository.save(any())).thenAnswer(i -> {
        PricePolicyAssignmentEntity e = i.getArgument(0);
        e.setId(3L);
        return e;
      });
      when(assignmentReadRepository.findEnrichedById(3L)).thenReturn(null);

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.CATEGORY, 5L, null);
      service.createAssignment(POLICY_ID, request, WORKSPACE_ID);

      verify(assignmentRepository).save(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getCategoryId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("throws NotFoundException when policy does not exist")
    void should_throwNotFound_when_policyNotExists() {
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      var request = new CreateAssignmentRequest(CONNECTION_ID, ScopeType.CONNECTION, null, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("deleteAssignment")
  class DeleteAssignment {

    @Test
    @DisplayName("deletes assignment when it belongs to policy")
    void should_delete_when_assignmentBelongsToPolicy() {
      mockPolicyExists();
      var assignment = new PricePolicyAssignmentEntity();
      assignment.setId(5L);
      assignment.setPricePolicyId(POLICY_ID);

      when(assignmentRepository.findById(5L)).thenReturn(Optional.of(assignment));

      service.deleteAssignment(POLICY_ID, 5L, WORKSPACE_ID);

      verify(assignmentRepository).delete(assignment);
    }

    @Test
    @DisplayName("throws NotFoundException when assignment not found")
    void should_throwNotFound_when_assignmentNotFound() {
      mockPolicyExists();
      when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deleteAssignment(POLICY_ID, 99L, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("throws NotFoundException when assignment belongs to different policy")
    void should_throwNotFound_when_assignmentPolicyMismatch() {
      mockPolicyExists();
      var assignment = new PricePolicyAssignmentEntity();
      assignment.setId(5L);
      assignment.setPricePolicyId(999L);

      when(assignmentRepository.findById(5L)).thenReturn(Optional.of(assignment));

      assertThatThrownBy(() -> service.deleteAssignment(POLICY_ID, 5L, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  private void mockPolicyExists() {
    var policy = new PricePolicyEntity();
    policy.setId(POLICY_ID);
    policy.setWorkspaceId(WORKSPACE_ID);
    when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
        .thenReturn(Optional.of(policy));
  }
}
