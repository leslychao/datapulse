package io.datapulse.promotions.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.CreatePromoPolicyRequest;
import io.datapulse.promotions.api.PromoPolicyMapper;
import io.datapulse.promotions.api.PromoPolicyResponse;
import io.datapulse.promotions.api.PromoPolicySummaryResponse;
import io.datapulse.promotions.api.UpdatePromoPolicyRequest;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import io.datapulse.promotions.persistence.PromoPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoPolicyServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long USER_ID = 10L;
  private static final long POLICY_ID = 100L;

  @Mock
  private PromoPolicyRepository policyRepository;

  @Mock
  private PromoPolicyMapper policyMapper;

  @InjectMocks
  private PromoPolicyService service;

  @Nested
  @DisplayName("createPolicy")
  class CreatePolicy {

    @Test
    void should_create_policy_with_draft_status_when_valid_request() {
      var request = new CreatePromoPolicyRequest(
          "Test Policy", ParticipationMode.SEMI_AUTO,
          new BigDecimal("10"), 7, new BigDecimal("30"),
          null, null, null);

      var saved = buildPolicy(PromoPolicyStatus.DRAFT, ParticipationMode.SEMI_AUTO);
      var response = buildResponse(saved);

      when(policyRepository.save(any())).thenReturn(saved);
      when(policyMapper.toResponse(saved)).thenReturn(response);

      PromoPolicyResponse result = service.createPolicy(request, WORKSPACE_ID, USER_ID);

      assertThat(result).isEqualTo(response);

      ArgumentCaptor<PromoPolicyEntity> captor = ArgumentCaptor.forClass(PromoPolicyEntity.class);
      verify(policyRepository).save(captor.capture());
      PromoPolicyEntity captured = captor.getValue();

      assertThat(captured.getStatus()).isEqualTo(PromoPolicyStatus.DRAFT);
      assertThat(captured.getVersion()).isEqualTo(1);
      assertThat(captured.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
      assertThat(captured.getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    void should_default_stock_days_to_7_when_not_provided() {
      var request = new CreatePromoPolicyRequest(
          "Test", ParticipationMode.FULL_AUTO,
          BigDecimal.TEN, null, null, null, null, null);

      when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(policyMapper.toResponse(any())).thenReturn(null);

      service.createPolicy(request, WORKSPACE_ID, USER_ID);

      ArgumentCaptor<PromoPolicyEntity> captor = ArgumentCaptor.forClass(PromoPolicyEntity.class);
      verify(policyRepository).save(captor.capture());
      assertThat(captor.getValue().getMinStockDaysOfCover()).isEqualTo(7);
    }
  }

  @Nested
  @DisplayName("listPolicies")
  class ListPolicies {

    @Test
    void should_return_all_policies_when_no_status_filter() {
      var entities = List.of(buildPolicy(PromoPolicyStatus.ACTIVE, ParticipationMode.SEMI_AUTO));
      when(policyRepository.findAllByWorkspaceId(WORKSPACE_ID)).thenReturn(entities);
      when(policyMapper.toSummaries(entities)).thenReturn(List.of());

      service.listPolicies(WORKSPACE_ID, null);

      verify(policyRepository).findAllByWorkspaceId(WORKSPACE_ID);
      verify(policyRepository, never()).findAllByWorkspaceIdAndStatus(any(), any());
    }

    @Test
    void should_filter_by_status_when_provided() {
      var entities = List.of(buildPolicy(PromoPolicyStatus.ACTIVE, ParticipationMode.SEMI_AUTO));
      when(policyRepository.findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PromoPolicyStatus.ACTIVE))
          .thenReturn(entities);
      when(policyMapper.toSummaries(entities)).thenReturn(List.of());

      service.listPolicies(WORKSPACE_ID, PromoPolicyStatus.ACTIVE);

      verify(policyRepository).findAllByWorkspaceIdAndStatus(WORKSPACE_ID, PromoPolicyStatus.ACTIVE);
    }
  }

  @Nested
  @DisplayName("updatePolicy")
  class UpdatePolicy {

    @Test
    void should_increment_version_when_logic_changed() {
      var entity = buildPolicy(PromoPolicyStatus.ACTIVE, ParticipationMode.SEMI_AUTO);
      entity.setVersion(1);
      entity.setMinMarginPct(new BigDecimal("10"));

      var request = new UpdatePromoPolicyRequest(
          "Updated", ParticipationMode.SEMI_AUTO,
          new BigDecimal("15"), null, null, null, null, null);

      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(policyMapper.toResponse(any())).thenReturn(null);

      service.updatePolicy(POLICY_ID, request, WORKSPACE_ID);

      assertThat(entity.getVersion()).isEqualTo(2);
    }

    @Test
    void should_not_increment_version_when_only_name_changed() {
      var entity = buildPolicy(PromoPolicyStatus.ACTIVE, ParticipationMode.SEMI_AUTO);
      entity.setVersion(3);
      entity.setMinMarginPct(new BigDecimal("10"));

      var request = new UpdatePromoPolicyRequest(
          "New Name", ParticipationMode.SEMI_AUTO,
          new BigDecimal("10"), entity.getMinStockDaysOfCover(),
          entity.getMaxPromoDiscountPct(), entity.getAutoParticipateCategories(),
          entity.getAutoDeclineCategories(), entity.getEvaluationConfig());

      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(policyMapper.toResponse(any())).thenReturn(null);

      service.updatePolicy(POLICY_ID, request, WORKSPACE_ID);

      assertThat(entity.getVersion()).isEqualTo(3);
    }

    @Test
    void should_throw_when_policy_is_archived() {
      var entity = buildPolicy(PromoPolicyStatus.ARCHIVED, ParticipationMode.SEMI_AUTO);
      var request = new UpdatePromoPolicyRequest(
          "X", ParticipationMode.SEMI_AUTO,
          BigDecimal.TEN, null, null, null, null, null);

      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.updatePolicy(POLICY_ID, request, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);

      verify(policyRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("activatePolicy")
  class ActivatePolicy {

    @Test
    void should_activate_when_draft() {
      var entity = buildPolicy(PromoPolicyStatus.DRAFT, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.activatePolicy(POLICY_ID, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PromoPolicyStatus.ACTIVE);
      verify(policyRepository).save(entity);
    }

    @Test
    void should_activate_when_paused() {
      var entity = buildPolicy(PromoPolicyStatus.PAUSED, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.activatePolicy(POLICY_ID, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PromoPolicyStatus.ACTIVE);
    }

    @Test
    void should_throw_when_already_active() {
      var entity = buildPolicy(PromoPolicyStatus.ACTIVE, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.activatePolicy(POLICY_ID, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_when_archived() {
      var entity = buildPolicy(PromoPolicyStatus.ARCHIVED, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.activatePolicy(POLICY_ID, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("pausePolicy")
  class PausePolicy {

    @Test
    void should_pause_when_active() {
      var entity = buildPolicy(PromoPolicyStatus.ACTIVE, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.pausePolicy(POLICY_ID, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PromoPolicyStatus.PAUSED);
    }

    @Test
    void should_throw_when_draft() {
      var entity = buildPolicy(PromoPolicyStatus.DRAFT, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.pausePolicy(POLICY_ID, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("archivePolicy")
  class ArchivePolicy {

    @Test
    void should_archive_active_policy() {
      var entity = buildPolicy(PromoPolicyStatus.ACTIVE, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.archivePolicy(POLICY_ID, WORKSPACE_ID);

      assertThat(entity.getStatus()).isEqualTo(PromoPolicyStatus.ARCHIVED);
      verify(policyRepository).save(entity);
    }

    @Test
    void should_noop_when_already_archived() {
      var entity = buildPolicy(PromoPolicyStatus.ARCHIVED, ParticipationMode.SEMI_AUTO);
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.archivePolicy(POLICY_ID, WORKSPACE_ID);

      verify(policyRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("getPolicy")
  class GetPolicy {

    @Test
    void should_throw_when_not_found() {
      when(policyRepository.findByIdAndWorkspaceId(POLICY_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getPolicy(POLICY_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  private PromoPolicyEntity buildPolicy(PromoPolicyStatus status, ParticipationMode mode) {
    var entity = new PromoPolicyEntity();
    entity.setId(POLICY_ID);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setName("Test Policy");
    entity.setStatus(status);
    entity.setParticipationMode(mode);
    entity.setMinMarginPct(new BigDecimal("10"));
    entity.setMinStockDaysOfCover(7);
    entity.setVersion(1);
    entity.setCreatedBy(USER_ID);
    return entity;
  }

  private PromoPolicyResponse buildResponse(PromoPolicyEntity entity) {
    return new PromoPolicyResponse(
        entity.getId(), entity.getName(), entity.getStatus(),
        entity.getParticipationMode(), entity.getMinMarginPct(),
        entity.getMinStockDaysOfCover(), entity.getMaxPromoDiscountPct(),
        null, null, null, entity.getVersion(),
        entity.getCreatedBy(), null, null);
  }
}
