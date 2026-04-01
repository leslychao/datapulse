package io.datapulse.promotions.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.CreatePromoAssignmentRequest;
import io.datapulse.promotions.api.PromoAssignmentMapper;
import io.datapulse.promotions.api.PromoAssignmentResponse;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentEntity;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentRepository;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import io.datapulse.promotions.persistence.PromoPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoPolicyAssignmentServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long POLICY_ID = 100L;
  private static final long ASSIGNMENT_ID = 200L;
  private static final long CONNECTION_ID = 5L;

  @Mock
  private PromoPolicyAssignmentRepository assignmentRepository;
  @Mock
  private PromoPolicyRepository policyRepository;
  @Mock
  private PromoAssignmentMapper assignmentMapper;

  @InjectMocks
  private PromoPolicyAssignmentService service;

  @Nested
  @DisplayName("createAssignment")
  class CreateAssignment {

    @Test
    void should_create_connection_scope_assignment() {
      mockPolicyExists();
      var request = new CreatePromoAssignmentRequest(
          CONNECTION_ID, PromoScopeType.CONNECTION, null, null);

      when(assignmentRepository.existsByPromoPolicyIdAndMarketplaceConnectionIdAndScopeType(
          POLICY_ID, CONNECTION_ID, PromoScopeType.CONNECTION)).thenReturn(false);
      when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(assignmentMapper.toResponse(any())).thenReturn(null);

      service.createAssignment(POLICY_ID, request, WORKSPACE_ID);

      verify(assignmentRepository).save(any(PromoPolicyAssignmentEntity.class));
    }

    @Test
    void should_throw_when_duplicate_connection_assignment() {
      mockPolicyExists();
      var request = new CreatePromoAssignmentRequest(
          CONNECTION_ID, PromoScopeType.CONNECTION, null, null);

      when(assignmentRepository.existsByPromoPolicyIdAndMarketplaceConnectionIdAndScopeType(
          POLICY_ID, CONNECTION_ID, PromoScopeType.CONNECTION)).thenReturn(true);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_when_sku_scope_without_offer_id() {
      mockPolicyExists();
      var request = new CreatePromoAssignmentRequest(
          CONNECTION_ID, PromoScopeType.SKU, null, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);

      verify(assignmentRepository, never()).save(any());
    }

    @Test
    void should_throw_when_category_scope_without_category_id() {
      mockPolicyExists();
      var request = new CreatePromoAssignmentRequest(
          CONNECTION_ID, PromoScopeType.CATEGORY, null, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_when_policy_not_found() {
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());
      var request = new CreatePromoAssignmentRequest(
          CONNECTION_ID, PromoScopeType.CONNECTION, null, null);

      assertThatThrownBy(() -> service.createAssignment(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("deleteAssignment")
  class DeleteAssignment {

    @Test
    void should_delete_when_assignment_belongs_to_policy() {
      mockPolicyExists();
      var assignment = new PromoPolicyAssignmentEntity();
      assignment.setId(ASSIGNMENT_ID);
      assignment.setPromoPolicyId(POLICY_ID);

      when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

      service.deleteAssignment(POLICY_ID, ASSIGNMENT_ID, WORKSPACE_ID);

      verify(assignmentRepository).delete(assignment);
    }

    @Test
    void should_throw_when_assignment_belongs_to_different_policy() {
      mockPolicyExists();
      var assignment = new PromoPolicyAssignmentEntity();
      assignment.setId(ASSIGNMENT_ID);
      assignment.setPromoPolicyId(999L);

      when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

      assertThatThrownBy(() ->
          service.deleteAssignment(POLICY_ID, ASSIGNMENT_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("listAssignments")
  class ListAssignments {

    @Test
    void should_return_assignments_for_existing_policy() {
      mockPolicyExists();
      when(assignmentRepository.findAllByPromoPolicyId(POLICY_ID)).thenReturn(List.of());
      when(assignmentMapper.toResponses(any())).thenReturn(List.of());

      service.listAssignments(POLICY_ID, WORKSPACE_ID);

      verify(assignmentRepository).findAllByPromoPolicyId(POLICY_ID);
    }

    @Test
    void should_throw_when_policy_not_found() {
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.listAssignments(POLICY_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  private void mockPolicyExists() {
    var entity = new PromoPolicyEntity();
    entity.setId(POLICY_ID);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setStatus(PromoPolicyStatus.ACTIVE);
    entity.setParticipationMode(ParticipationMode.SEMI_AUTO);
    entity.setMinMarginPct(BigDecimal.TEN);
    entity.setMinStockDaysOfCover(7);
    entity.setVersion(1);
    entity.setCreatedBy(1L);
    entity.setName("Test");
    when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
        .thenReturn(Optional.of(entity));
  }
}
