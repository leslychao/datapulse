package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.audit.AuditPublisher;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock
  private WorkspaceMemberRepository memberRepository;
  @Mock
  private WorkspaceRepository workspaceRepository;
  @Mock
  private AuditPublisher auditPublisher;

  @InjectMocks
  private MemberService memberService;

  private WorkspaceMemberEntity buildMember(Long userId, MemberRole role) {
    var user = new AppUserEntity();
    user.setId(userId);
    user.setEmail("user" + userId + "@test.com");
    user.setName("User " + userId);

    var member = new WorkspaceMemberEntity();
    member.setUser(user);
    member.setRole(role);
    member.setStatus(MemberStatus.ACTIVE);
    return member;
  }

  @Nested
  @DisplayName("changeRole")
  class ChangeRole {

    @Test
    @DisplayName("should_change_role_when_valid_request")
    void should_change_role_when_valid_request() {
      var member = buildMember(20L, MemberRole.VIEWER);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 20L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));
      when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      memberService.changeRole(1L, 20L, 10L, "OWNER", MemberRole.ANALYST);

      assertThat(member.getRole()).isEqualTo(MemberRole.ANALYST);
      verify(auditPublisher).publish(
          eq("member.change_role"), eq("workspace_member"), eq("20"), anyString());
    }

    @Test
    @DisplayName("should_throw_when_target_is_owner")
    void should_throw_when_target_is_owner() {
      var member = buildMember(20L, MemberRole.OWNER);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 20L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));

      assertThatThrownBy(() -> memberService.changeRole(
          1L, 20L, 10L, "OWNER", MemberRole.ADMIN))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.role.cannot.change.owner");
    }

    @Test
    @DisplayName("should_throw_when_changing_own_role")
    void should_throw_when_changing_own_role() {
      var member = buildMember(10L, MemberRole.ADMIN);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 10L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));

      assertThatThrownBy(() -> memberService.changeRole(
          1L, 10L, 10L, "OWNER", MemberRole.VIEWER))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.role.cannot.change.self");
    }

    @Test
    @DisplayName("should_throw_when_assigning_owner_role")
    void should_throw_when_assigning_owner_role() {
      var member = buildMember(20L, MemberRole.VIEWER);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 20L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));

      assertThatThrownBy(() -> memberService.changeRole(
          1L, 20L, 10L, "OWNER", MemberRole.OWNER))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.role.cannot.assign.owner");
    }

    @Test
    @DisplayName("should_throw_when_admin_assigns_admin")
    void should_throw_when_admin_assigns_admin() {
      var member = buildMember(20L, MemberRole.VIEWER);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 20L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));

      assertThatThrownBy(() -> memberService.changeRole(
          1L, 20L, 10L, "ADMIN", MemberRole.ADMIN))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.role.admin.cannot.assign.admin");
    }

    @Test
    @DisplayName("should_throw_not_found_when_member_missing")
    void should_throw_not_found_when_member_missing() {
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 99L, MemberStatus.ACTIVE))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> memberService.changeRole(
          1L, 99L, 10L, "OWNER", MemberRole.VIEWER))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("removeMember")
  class RemoveMember {

    @Test
    @DisplayName("should_deactivate_member_when_valid")
    void should_deactivate_member_when_valid() {
      var member = buildMember(20L, MemberRole.VIEWER);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 20L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));
      when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      memberService.removeMember(1L, 20L, 10L);

      assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("should_throw_when_removing_owner")
    void should_throw_when_removing_owner() {
      var member = buildMember(20L, MemberRole.OWNER);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 20L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));

      assertThatThrownBy(() -> memberService.removeMember(1L, 20L, 10L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.cannot.remove.owner");
    }

    @Test
    @DisplayName("should_throw_when_removing_self")
    void should_throw_when_removing_self() {
      var member = buildMember(10L, MemberRole.ADMIN);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 10L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));

      assertThatThrownBy(() -> memberService.removeMember(1L, 10L, 10L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.cannot.remove.self");
    }
  }

  @Nested
  @DisplayName("transferOwnership")
  class TransferOwnership {

    @Test
    @DisplayName("should_transfer_ownership_when_valid")
    void should_transfer_ownership_when_valid() {
      var currentOwner = buildMember(10L, MemberRole.OWNER);
      var newOwner = buildMember(20L, MemberRole.ADMIN);
      var workspace = new WorkspaceEntity();
      workspace.setId(1L);
      workspace.setOwnerUserId(10L);

      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 10L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(currentOwner));
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 20L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(newOwner));
      when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
      when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(workspaceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      memberService.transferOwnership(1L, 10L, 20L);

      assertThat(currentOwner.getRole()).isEqualTo(MemberRole.ADMIN);
      assertThat(newOwner.getRole()).isEqualTo(MemberRole.OWNER);
      assertThat(workspace.getOwnerUserId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("should_throw_when_transferring_to_self")
    void should_throw_when_transferring_to_self() {
      assertThatThrownBy(() -> memberService.transferOwnership(1L, 10L, 10L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.transfer.self");
    }

    @Test
    @DisplayName("should_throw_when_current_user_is_not_owner")
    void should_throw_when_current_user_is_not_owner() {
      var currentMember = buildMember(10L, MemberRole.ADMIN);
      when(memberRepository.findByWorkspace_IdAndUser_IdAndStatus(1L, 10L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(currentMember));

      assertThatThrownBy(() -> memberService.transferOwnership(1L, 10L, 20L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("member.role.cannot.change.owner");
    }
  }

  @Nested
  @DisplayName("listMembers")
  class ListMembers {

    @Test
    @DisplayName("should_return_only_active_members")
    void should_return_only_active_members() {
      var m1 = buildMember(1L, MemberRole.OWNER);
      var m2 = buildMember(2L, MemberRole.ADMIN);
      when(memberRepository.findByWorkspace_IdAndStatus(5L, MemberStatus.ACTIVE))
          .thenReturn(List.of(m1, m2));

      List<WorkspaceMemberEntity> result = memberService.listMembers(5L);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getRole()).isEqualTo(MemberRole.OWNER);
    }
  }
}
