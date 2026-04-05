package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantEntity;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

  @Mock
  private AppUserRepository appUserRepository;
  @Mock
  private WorkspaceMemberRepository memberRepository;

  @InjectMocks
  private UserProfileService userProfileService;

  private AppUserEntity buildUser(Long id, String email, String name) {
    var user = new AppUserEntity();
    user.setId(id);
    user.setEmail(email);
    user.setName(name);
    return user;
  }

  @Nested
  @DisplayName("getProfile")
  class GetProfile {

    @Test
    @DisplayName("should_return_profile_with_memberships_when_user_has_workspaces")
    void should_return_profile_with_memberships_when_user_has_workspaces() {
      var user = buildUser(1L, "user@test.com", "John");
      when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

      var tenant = new TenantEntity();
      tenant.setId(10L);
      tenant.setName("Acme");

      var workspace = new WorkspaceEntity();
      workspace.setId(100L);
      workspace.setName("Shop");
      workspace.setTenant(tenant);

      var member = new WorkspaceMemberEntity();
      member.setWorkspace(workspace);
      member.setRole(MemberRole.OWNER);
      member.setStatus(MemberStatus.ACTIVE);

      when(memberRepository.findByUser_IdAndStatus(1L, MemberStatus.ACTIVE))
          .thenReturn(List.of(member));

      UserProfile profile = userProfileService.getProfile(1L);

      assertThat(profile.id()).isEqualTo(1L);
      assertThat(profile.email()).isEqualTo("user@test.com");
      assertThat(profile.needsOnboarding()).isFalse();
      assertThat(profile.memberships()).hasSize(1);
      assertThat(profile.memberships().get(0).workspaceName()).isEqualTo("Shop");
      assertThat(profile.memberships().get(0).role()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("should_set_needs_onboarding_when_no_memberships")
    void should_set_needs_onboarding_when_no_memberships() {
      var user = buildUser(1L, "new@test.com", "New User");
      when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
      when(memberRepository.findByUser_IdAndStatus(1L, MemberStatus.ACTIVE))
          .thenReturn(List.of());

      UserProfile profile = userProfileService.getProfile(1L);

      assertThat(profile.needsOnboarding()).isTrue();
      assertThat(profile.memberships()).isEmpty();
    }

    @Test
    @DisplayName("should_throw_not_found_when_user_missing")
    void should_throw_not_found_when_user_missing() {
      when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userProfileService.getProfile(99L))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("updateProfile")
  class UpdateProfile {

    @Test
    @DisplayName("should_update_name_and_return_profile_when_valid")
    void should_update_name_and_return_profile_when_valid() {
      var user = buildUser(1L, "user@test.com", "Old Name");
      when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
      when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(memberRepository.findByUser_IdAndStatus(1L, MemberStatus.ACTIVE))
          .thenReturn(List.of());

      userProfileService.updateProfile(1L, "  New Name  ");

      verify(appUserRepository).save(user);
      assertThat(user.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("should_throw_not_found_when_user_missing")
    void should_throw_not_found_when_user_missing() {
      when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userProfileService.updateProfile(99L, "Name"))
          .isInstanceOf(NotFoundException.class);
    }
  }
}
