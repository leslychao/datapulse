package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.audit.AuditPublisher;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantEntity;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

  @Mock
  private TenantRepository tenantRepository;
  @Mock
  private WorkspaceRepository workspaceRepository;
  @Mock
  private WorkspaceMemberRepository memberRepository;
  @Mock
  private AppUserRepository appUserRepository;
  @Mock
  private AuditPublisher auditPublisher;

  @InjectMocks
  private OnboardingService onboardingService;

  @Nested
  @DisplayName("createTenant")
  class CreateTenant {

    @Test
    @DisplayName("should_create_tenant_when_valid_request")
    void should_create_tenant_when_valid_request() {
      when(tenantRepository.countByOwnerUserId(1L)).thenReturn(0L);
      when(tenantRepository.existsBySlug(anyString())).thenReturn(false);
      when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> {
        TenantEntity t = inv.getArgument(0);
        t.setId(10L);
        return t;
      });

      TenantEntity response = onboardingService.createTenant("My Store", 1L);

      assertThat(response.getName()).isEqualTo("My Store");
      assertThat(response.getSlug()).isEqualTo("my-store");
      verify(auditPublisher).publish("tenant.create", "tenant", "10");
    }

    @Test
    @DisplayName("should_throw_when_tenant_limit_exceeded")
    void should_throw_when_tenant_limit_exceeded() {
      when(tenantRepository.countByOwnerUserId(1L)).thenReturn(3L);

      assertThatThrownBy(() -> onboardingService.createTenant("New", 1L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("tenant.limit.exceeded");

      verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_generate_unique_slug_when_collision_occurs")
    void should_generate_unique_slug_when_collision_occurs() {
      when(tenantRepository.countByOwnerUserId(1L)).thenReturn(0L);
      when(tenantRepository.existsBySlug(anyString()))
          .thenReturn(true)
          .thenReturn(true)
          .thenReturn(false);
      when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> {
        TenantEntity t = inv.getArgument(0);
        t.setId(10L);
        return t;
      });

      TenantEntity response = onboardingService.createTenant("My Store", 1L);

      assertThat(response.getSlug()).isNotEqualTo("my-store");
      assertThat(response.getSlug()).startsWith("my-store-");
    }

    @Test
    @DisplayName("should_throw_when_all_slug_retries_exhausted")
    void should_throw_when_all_slug_retries_exhausted() {
      when(tenantRepository.countByOwnerUserId(1L)).thenReturn(0L);
      when(tenantRepository.existsBySlug(anyString())).thenReturn(true);

      assertThatThrownBy(() -> onboardingService.createTenant("Test", 1L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to generate unique slug");
    }

    @Test
    @DisplayName("should_trim_name_when_whitespace_present")
    void should_trim_name_when_whitespace_present() {
      when(tenantRepository.countByOwnerUserId(1L)).thenReturn(0L);
      when(tenantRepository.existsBySlug(anyString())).thenReturn(false);
      when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> {
        TenantEntity t = inv.getArgument(0);
        t.setId(1L);
        return t;
      });

      TenantEntity response = onboardingService.createTenant("  My Store  ", 1L);

      assertThat(response.getName()).isEqualTo("My Store");
    }
  }

  @Nested
  @DisplayName("getTenant")
  class GetTenant {

    @Test
    @DisplayName("should_return_tenant_when_found")
    void should_return_tenant_when_found() {
      var tenant = new TenantEntity();
      tenant.setId(5L);
      tenant.setName("Acme");
      tenant.setSlug("acme");
      when(tenantRepository.findById(5L)).thenReturn(Optional.of(tenant));

      TenantEntity response = onboardingService.getTenant(5L);

      assertThat(response.getId()).isEqualTo(5L);
      assertThat(response.getName()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("should_throw_not_found_when_tenant_missing")
    void should_throw_not_found_when_tenant_missing() {
      when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> onboardingService.getTenant(99L))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("createWorkspace")
  class CreateWorkspace {

    @Test
    @DisplayName("should_create_workspace_and_owner_membership_when_valid")
    void should_create_workspace_and_owner_membership_when_valid() {
      var tenant = new TenantEntity();
      tenant.setId(1L);
      tenant.setName("Acme");
      tenant.setOwnerUserId(10L);
      when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

      var owner = new AppUserEntity();
      owner.setId(10L);
      when(appUserRepository.findById(10L)).thenReturn(Optional.of(owner));

      when(workspaceRepository.existsByTenant_IdAndSlug(1L, "main-shop"))
          .thenReturn(false);
      when(workspaceRepository.save(any(WorkspaceEntity.class))).thenAnswer(inv -> {
        WorkspaceEntity ws = inv.getArgument(0);
        ws.setId(100L);
        return ws;
      });
      when(memberRepository.save(any(WorkspaceMemberEntity.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      WorkspaceSummary response = onboardingService.createWorkspace(1L, "Main Shop", 10L);

      assertThat(response.name()).isEqualTo("Main Shop");
      assertThat(response.membersCount()).isEqualTo(1);
      assertThat(response.connectionsCount()).isEqualTo(0);

      ArgumentCaptor<WorkspaceMemberEntity> captor =
          ArgumentCaptor.forClass(WorkspaceMemberEntity.class);
      verify(memberRepository).save(captor.capture());
      assertThat(captor.getValue().getRole()).isEqualTo(MemberRole.OWNER);
      assertThat(captor.getValue().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("should_throw_when_tenant_not_found")
    void should_throw_when_tenant_not_found() {
      when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> onboardingService.createWorkspace(99L, "X", 1L))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("should_throw_when_user_is_not_tenant_owner")
    void should_throw_when_user_is_not_tenant_owner() {
      var tenant = new TenantEntity();
      tenant.setId(1L);
      tenant.setOwnerUserId(10L);
      when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

      assertThatThrownBy(() -> onboardingService.createWorkspace(1L, "X", 99L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("tenant.not.owner");
    }
  }
}
