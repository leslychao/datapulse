package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.CreatePricePolicyRequest;
import io.datapulse.pricing.api.PricePolicyMapper;
import io.datapulse.pricing.api.PricePolicyResponse;
import io.datapulse.pricing.api.UpdatePricePolicyRequest;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricePolicyRepository;

@ExtendWith(MockitoExtension.class)
class PricePolicyServiceTest {

  @Mock private PricePolicyRepository policyRepository;
  @Mock private PricePolicyMapper policyMapper;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks
  private PricePolicyService service;

  @Captor
  private ArgumentCaptor<PricePolicyEntity> entityCaptor;

  private static final long WORKSPACE_ID = 10L;
  private static final long USER_ID = 1L;

  @Nested
  @DisplayName("createPolicy")
  class CreatePolicy {

    @Test
    @DisplayName("creates policy with DRAFT status and version 1")
    void should_createDraftPolicy_when_validRequest() throws Exception {
      CreatePricePolicyRequest request = new CreatePricePolicyRequest(
          "Margin Policy", PolicyType.TARGET_MARGIN, "{\"targetMarginPct\": 0.20}",
          null, null, null, null, null, ExecutionMode.RECOMMENDATION, null);

      when(objectMapper.readValue(eq("{\"targetMarginPct\": 0.20}"), eq(TargetMarginParams.class)))
          .thenReturn(new TargetMarginParams(
              new BigDecimal("0.20"), null, null, null, null, null, null, null, null, null, null));
      when(policyRepository.save(any())).thenAnswer(i -> {
        PricePolicyEntity e = i.getArgument(0);
        e.setId(1L);
        return e;
      });
      when(policyMapper.toResponse(any())).thenReturn(null);

      service.createPolicy(request, WORKSPACE_ID, USER_ID);

      verify(policyRepository).save(entityCaptor.capture());
      PricePolicyEntity saved = entityCaptor.getValue();
      assertThat(saved.getStatus()).isEqualTo(PolicyStatus.DRAFT);
      assertThat(saved.getVersion()).isEqualTo(1);
      assertThat(saved.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
      assertThat(saved.getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("rejects MANUAL_OVERRIDE strategy type")
    void should_reject_when_manualOverrideType() {
      CreatePricePolicyRequest request = new CreatePricePolicyRequest(
          "Manual", PolicyType.MANUAL_OVERRIDE, "{}",
          null, null, null, null, null, ExecutionMode.RECOMMENDATION, null);

      assertThatThrownBy(() -> service.createPolicy(request, WORKSPACE_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects null strategy params")
    void should_reject_when_nullStrategyParams() {
      CreatePricePolicyRequest request = new CreatePricePolicyRequest(
          "Policy", PolicyType.TARGET_MARGIN, null,
          null, null, null, null, null, ExecutionMode.RECOMMENDATION, null);

      assertThatThrownBy(() -> service.createPolicy(request, WORKSPACE_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("sets approval timeout to 72 for SEMI_AUTO")
    void should_setApprovalTimeout72_when_semiAuto() throws Exception {
      CreatePricePolicyRequest request = new CreatePricePolicyRequest(
          "Semi Auto", PolicyType.TARGET_MARGIN, "{\"targetMarginPct\": 0.15}",
          null, null, null, null, null, ExecutionMode.SEMI_AUTO, null);

      when(objectMapper.readValue(any(String.class), eq(TargetMarginParams.class)))
          .thenReturn(new TargetMarginParams(
              new BigDecimal("0.15"), null, null, null, null, null, null, null, null, null, null));
      when(policyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(policyMapper.toResponse(any())).thenReturn(null);

      service.createPolicy(request, WORKSPACE_ID, USER_ID);

      verify(policyRepository).save(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getApprovalTimeoutHours()).isEqualTo(72);
    }
  }

  @Nested
  @DisplayName("activatePolicy")
  class ActivatePolicy {

    @Test
    @DisplayName("activates DRAFT policy")
    void should_activate_when_statusDraft() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.DRAFT);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      service.activatePolicy(1L, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    @DisplayName("activates PAUSED policy")
    void should_activate_when_statusPaused() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.PAUSED);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      service.activatePolicy(1L, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    @DisplayName("rejects activation from ARCHIVED status")
    void should_reject_when_statusArchived() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ARCHIVED);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.activatePolicy(1L, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects activation from ACTIVE status")
    void should_reject_when_alreadyActive() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ACTIVE);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.activatePolicy(1L, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("throws NotFoundException when policy not found")
    void should_throwNotFound_when_policyNotFound() {
      when(policyRepository.findByIdAndWorkspaceId(99L, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.activatePolicy(99L, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("pausePolicy")
  class PausePolicy {

    @Test
    @DisplayName("pauses ACTIVE policy")
    void should_pause_when_statusActive() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ACTIVE);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      service.pausePolicy(1L, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PolicyStatus.PAUSED);
    }

    @Test
    @DisplayName("rejects pause from DRAFT status")
    void should_reject_when_statusDraft() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.DRAFT);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.pausePolicy(1L, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects pause from ARCHIVED status")
    void should_reject_when_statusArchived() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ARCHIVED);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.pausePolicy(1L, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("archivePolicy")
  class ArchivePolicy {

    @Test
    @DisplayName("archives ACTIVE policy")
    void should_archive_when_statusActive() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ACTIVE);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      service.archivePolicy(1L, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PolicyStatus.ARCHIVED);
    }

    @Test
    @DisplayName("no-op when already archived")
    void should_doNothing_when_alreadyArchived() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ARCHIVED);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.archivePolicy(1L, WORKSPACE_ID);

      verify(policyRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("updatePolicy")
  class UpdatePolicy {

    @Test
    @DisplayName("rejects update of ARCHIVED policy")
    void should_reject_when_policyArchived() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ARCHIVED);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      UpdatePricePolicyRequest request = new UpdatePricePolicyRequest(
          "Updated", PolicyType.TARGET_MARGIN, "{\"targetMarginPct\": 0.25}",
          null, null, null, null, null, ExecutionMode.RECOMMENDATION, null);

      assertThatThrownBy(() -> service.updatePolicy(1L, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("increments version when logic changes")
    void should_incrementVersion_when_logicChanges() throws Exception {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ACTIVE);
      entity.setStrategyType(PolicyType.TARGET_MARGIN);
      entity.setStrategyParams("{\"targetMarginPct\": 0.20}");
      entity.setExecutionMode(ExecutionMode.RECOMMENDATION);
      entity.setVersion(1);
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(policyMapper.toResponse(any())).thenReturn(null);

      String newParams = "{\"targetMarginPct\": 0.30}";
      when(objectMapper.readValue(eq(newParams), eq(TargetMarginParams.class)))
          .thenReturn(new TargetMarginParams(
              new BigDecimal("0.30"), null, null, null, null, null, null, null, null, null, null));

      UpdatePricePolicyRequest request = new UpdatePricePolicyRequest(
          "Updated", PolicyType.TARGET_MARGIN, newParams,
          null, null, null, null, null, ExecutionMode.RECOMMENDATION, null);

      service.updatePolicy(1L, request, WORKSPACE_ID);

      assertThat(entity.getVersion()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("getPolicy")
  class GetPolicy {

    @Test
    @DisplayName("returns response when found")
    void should_returnResponse_when_policyExists() {
      PricePolicyEntity entity = policyEntity(PolicyStatus.ACTIVE);
      PricePolicyResponse mockResponse = null;
      when(policyRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyMapper.toResponse(entity)).thenReturn(mockResponse);

      service.getPolicy(1L, WORKSPACE_ID);

      verify(policyMapper).toResponse(entity);
    }

    @Test
    @DisplayName("throws NotFoundException when policy not found")
    void should_throwNotFound_when_policyNotFound() {
      when(policyRepository.findByIdAndWorkspaceId(99L, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getPolicy(99L, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("listPolicies — filter combinations")
  class ListPolicies {

    @Test
    @DisplayName("filters by status and strategy type when both provided")
    void should_filterByBoth_when_statusAndTypeProvided() {
      when(policyRepository.findAllByWorkspaceIdAndStatusAndStrategyType(
          WORKSPACE_ID, PolicyStatus.ACTIVE, PolicyType.TARGET_MARGIN))
          .thenReturn(List.of());

      service.listPolicies(WORKSPACE_ID, PolicyStatus.ACTIVE, PolicyType.TARGET_MARGIN);

      verify(policyRepository).findAllByWorkspaceIdAndStatusAndStrategyType(
          WORKSPACE_ID, PolicyStatus.ACTIVE, PolicyType.TARGET_MARGIN);
    }

    @Test
    @DisplayName("filters by status only when type is null")
    void should_filterByStatusOnly_when_typeNull() {
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE))
          .thenReturn(List.of());

      service.listPolicies(WORKSPACE_ID, PolicyStatus.ACTIVE, null);

      verify(policyRepository).findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PolicyStatus.ACTIVE);
    }

    @Test
    @DisplayName("returns all when both filters are null")
    void should_returnAll_when_noFilters() {
      when(policyRepository.findAllByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

      service.listPolicies(WORKSPACE_ID, null, null);

      verify(policyRepository).findAllByWorkspaceId(WORKSPACE_ID);
    }
  }

  private PricePolicyEntity policyEntity(PolicyStatus status) {
    var entity = new PricePolicyEntity();
    entity.setId(1L);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setName("Test Policy");
    entity.setStatus(status);
    entity.setStrategyType(PolicyType.TARGET_MARGIN);
    entity.setStrategyParams("{}");
    entity.setExecutionMode(ExecutionMode.RECOMMENDATION);
    entity.setVersion(1);
    entity.setPriority(0);
    entity.setCreatedBy(USER_ID);
    entity.setApprovalTimeoutHours(0);
    return entity;
  }
}
