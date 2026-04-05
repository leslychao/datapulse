package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.AppException;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.audit.AuditPublisher;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceInvitationEntity;
import io.datapulse.tenancy.persistence.WorkspaceInvitationRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

  @Mock
  private WorkspaceInvitationRepository invitationRepository;
  @Mock
  private WorkspaceMemberRepository memberRepository;
  @Mock
  private WorkspaceRepository workspaceRepository;
  @Mock
  private AppUserRepository appUserRepository;
  @Mock
  private AuditPublisher auditPublisher;

  private InvitationService invitationService;

  @BeforeEach
  void setUp() {
    invitationService = new InvitationService(
        invitationRepository, memberRepository, workspaceRepository,
        appUserRepository, Optional.empty(), auditPublisher);
  }

  @Nested
  @DisplayName("createInvitation")
  class CreateInvitation {

    @Test
    @DisplayName("should_create_invitation_when_valid_request")
    void should_create_invitation_when_valid_request() {
      var workspace = new WorkspaceEntity();
      workspace.setId(1L);
      workspace.setName("Test WS");
      when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
      when(invitationRepository.findByWorkspace_IdAndEmailAndStatus(
          eq(1L), anyString(), eq(InvitationStatus.PENDING)))
          .thenReturn(Optional.empty());
      when(invitationRepository.save(any(WorkspaceInvitationEntity.class)))
          .thenAnswer(inv -> {
            WorkspaceInvitationEntity e = inv.getArgument(0);
            e.setId(50L);
            return e;
          });

      WorkspaceInvitationEntity response = invitationService.createInvitation(
          1L, 10L, "OWNER", "user@example.com", MemberRole.ANALYST);

      assertThat(response.getId()).isEqualTo(50L);
      assertThat(response.getRole()).isEqualTo(MemberRole.ANALYST);
      assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);
      verify(auditPublisher).publish(eq("member.invite"), eq("workspace_invitation"), anyString());
    }

    @Test
    @DisplayName("should_throw_when_inviting_as_owner_role")
    void should_throw_when_inviting_as_owner_role() {
      assertThatThrownBy(() -> invitationService.createInvitation(
          1L, 10L, "OWNER", "u@e.com", MemberRole.OWNER))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("invitation.cannot.assign.owner");
    }

    @Test
    @DisplayName("should_throw_when_admin_invites_admin")
    void should_throw_when_admin_invites_admin() {
      assertThatThrownBy(() -> invitationService.createInvitation(
          1L, 10L, "ADMIN", "u@e.com", MemberRole.ADMIN))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("invitation.admin.cannot.invite.admin");
    }

    @Test
    @DisplayName("should_throw_conflict_when_user_already_active_member")
    void should_throw_conflict_when_user_already_active_member() {
      var existingUser = new AppUserEntity();
      existingUser.setId(20L);
      when(appUserRepository.findByEmail("user@example.com"))
          .thenReturn(Optional.of(existingUser));
      when(memberRepository.existsByWorkspace_IdAndUser_IdAndStatus(
          1L, 20L, MemberStatus.ACTIVE)).thenReturn(true);

      assertThatThrownBy(() -> invitationService.createInvitation(
          1L, 10L, "OWNER", "user@example.com", MemberRole.ANALYST))
          .isInstanceOf(ConflictException.class)
          .hasMessage("invitation.user.already.member");
    }

    @Test
    @DisplayName("should_update_existing_pending_invitation_when_found")
    void should_update_existing_pending_invitation_when_found() {
      var workspace = new WorkspaceEntity();
      workspace.setId(1L);
      workspace.setName("WS");
      when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

      var existing = new WorkspaceInvitationEntity();
      existing.setId(30L);
      existing.setEmail("u@e.com");
      existing.setRole(MemberRole.VIEWER);
      existing.setStatus(InvitationStatus.PENDING);
      existing.setExpiresAt(OffsetDateTime.now().plusDays(7));
      when(invitationRepository.findByWorkspace_IdAndEmailAndStatus(
          eq(1L), eq("u@e.com"), eq(InvitationStatus.PENDING)))
          .thenReturn(Optional.of(existing));
      when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      WorkspaceInvitationEntity response = invitationService.createInvitation(
          1L, 10L, "OWNER", "u@e.com", MemberRole.ADMIN);

      assertThat(response.getRole()).isEqualTo(MemberRole.ADMIN);
      verify(auditPublisher, never()).publish(anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("cancelInvitation")
  class CancelInvitation {

    @Test
    @DisplayName("should_cancel_when_invitation_is_pending")
    void should_cancel_when_invitation_is_pending() {
      var inv = new WorkspaceInvitationEntity();
      inv.setId(1L);
      inv.setStatus(InvitationStatus.PENDING);
      when(invitationRepository.findByIdAndWorkspace_Id(1L, 10L))
          .thenReturn(Optional.of(inv));
      when(invitationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      invitationService.cancelInvitation(10L, 1L);

      assertThat(inv.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
    }

    @Test
    @DisplayName("should_throw_when_invitation_not_pending")
    void should_throw_when_invitation_not_pending() {
      var inv = new WorkspaceInvitationEntity();
      inv.setId(1L);
      inv.setStatus(InvitationStatus.ACCEPTED);
      when(invitationRepository.findByIdAndWorkspace_Id(1L, 10L))
          .thenReturn(Optional.of(inv));

      assertThatThrownBy(() -> invitationService.cancelInvitation(10L, 1L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("invitation.not.pending");
    }

    @Test
    @DisplayName("should_throw_not_found_when_invitation_missing")
    void should_throw_not_found_when_invitation_missing() {
      when(invitationRepository.findByIdAndWorkspace_Id(99L, 10L))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> invitationService.cancelInvitation(10L, 99L))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("resendInvitation")
  class ResendInvitation {

    @Test
    @DisplayName("should_regenerate_token_and_extend_expiry_when_pending")
    void should_regenerate_token_and_extend_expiry_when_pending() {
      var workspace = new WorkspaceEntity();
      workspace.setId(10L);
      workspace.setName("WS");

      var inv = new WorkspaceInvitationEntity();
      inv.setId(1L);
      inv.setStatus(InvitationStatus.PENDING);
      inv.setTokenHash("old-hash");
      inv.setExpiresAt(OffsetDateTime.now().minusDays(1));
      inv.setInvitedByUserId(5L);
      inv.setWorkspace(workspace);
      inv.setEmail("u@e.com");
      inv.setRole(MemberRole.ANALYST);
      when(invitationRepository.findByIdAndWorkspace_Id(1L, 10L))
          .thenReturn(Optional.of(inv));
      when(invitationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WorkspaceInvitationEntity response = invitationService.resendInvitation(10L, 1L);

      assertThat(inv.getTokenHash()).isNotEqualTo("old-hash");
      assertThat(inv.getExpiresAt()).isAfter(OffsetDateTime.now());
      assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_throw_when_not_pending")
    void should_throw_when_not_pending() {
      var inv = new WorkspaceInvitationEntity();
      inv.setId(1L);
      inv.setStatus(InvitationStatus.EXPIRED);
      when(invitationRepository.findByIdAndWorkspace_Id(1L, 10L))
          .thenReturn(Optional.of(inv));

      assertThatThrownBy(() -> invitationService.resendInvitation(10L, 1L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("invitation.not.pending");
    }
  }

  @Nested
  @DisplayName("acceptInvitation")
  class AcceptInvitation {

    @Test
    @DisplayName("should_create_membership_when_token_valid_and_no_existing_member")
    void should_create_membership_when_token_valid_and_no_existing_member() {
      var workspace = new WorkspaceEntity();
      workspace.setId(1L);
      workspace.setName("Main WS");

      var inv = new WorkspaceInvitationEntity();
      inv.setId(10L);
      inv.setStatus(InvitationStatus.PENDING);
      inv.setRole(MemberRole.ANALYST);
      inv.setExpiresAt(OffsetDateTime.now().plusDays(3));
      inv.setWorkspace(workspace);

      when(invitationRepository.findByTokenHash(anyString()))
          .thenReturn(Optional.of(inv));

      var user = new AppUserEntity();
      user.setId(50L);
      when(appUserRepository.findById(50L)).thenReturn(Optional.of(user));
      when(memberRepository.findByWorkspace_IdAndUser_Id(1L, 50L))
          .thenReturn(Optional.empty());
      when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(invitationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WorkspaceInvitationEntity response = invitationService.acceptInvitation(
          "some-raw-token", 50L);

      assertThat(response.getWorkspace().getId()).isEqualTo(1L);
      assertThat(response.getWorkspace().getName()).isEqualTo("Main WS");
      assertThat(response.getRole()).isEqualTo(MemberRole.ANALYST);
      assertThat(inv.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
      assertThat(inv.getAcceptedByUserId()).isEqualTo(50L);

      ArgumentCaptor<WorkspaceMemberEntity> captor =
          ArgumentCaptor.forClass(WorkspaceMemberEntity.class);
      verify(memberRepository).save(captor.capture());
      assertThat(captor.getValue().getRole()).isEqualTo(MemberRole.ANALYST);
    }

    @Test
    @DisplayName("should_throw_not_found_when_token_invalid")
    void should_throw_not_found_when_token_invalid() {
      when(invitationRepository.findByTokenHash(anyString()))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> invitationService.acceptInvitation("bad-token", 1L))
          .isInstanceOf(NotFoundException.class)
          .hasMessage("invitation.not.found");
    }

    @Test
    @DisplayName("should_throw_conflict_when_already_accepted")
    void should_throw_conflict_when_already_accepted() {
      var inv = new WorkspaceInvitationEntity();
      inv.setStatus(InvitationStatus.ACCEPTED);
      when(invitationRepository.findByTokenHash(anyString()))
          .thenReturn(Optional.of(inv));

      assertThatThrownBy(() -> invitationService.acceptInvitation("token", 1L))
          .isInstanceOf(ConflictException.class)
          .hasMessage("invitation.already.accepted");
    }

    @Test
    @DisplayName("should_throw_410_when_invitation_expired")
    void should_throw_410_when_invitation_expired() {
      var inv = new WorkspaceInvitationEntity();
      inv.setStatus(InvitationStatus.PENDING);
      inv.setExpiresAt(OffsetDateTime.now().minusDays(1));
      when(invitationRepository.findByTokenHash(anyString()))
          .thenReturn(Optional.of(inv));
      when(invitationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      assertThatThrownBy(() -> invitationService.acceptInvitation("token", 1L))
          .isInstanceOf(AppException.class)
          .hasMessage("invitation.expired")
          .extracting("statusCode").isEqualTo(410);
    }

    @Test
    @DisplayName("should_reactivate_inactive_membership_when_exists")
    void should_reactivate_inactive_membership_when_exists() {
      var workspace = new WorkspaceEntity();
      workspace.setId(1L);
      workspace.setName("WS");

      var inv = new WorkspaceInvitationEntity();
      inv.setId(10L);
      inv.setStatus(InvitationStatus.PENDING);
      inv.setRole(MemberRole.OPERATOR);
      inv.setExpiresAt(OffsetDateTime.now().plusDays(3));
      inv.setWorkspace(workspace);

      when(invitationRepository.findByTokenHash(anyString()))
          .thenReturn(Optional.of(inv));

      var user = new AppUserEntity();
      user.setId(50L);
      when(appUserRepository.findById(50L)).thenReturn(Optional.of(user));

      var existingMember = new WorkspaceMemberEntity();
      existingMember.setStatus(MemberStatus.INACTIVE);
      existingMember.setRole(MemberRole.VIEWER);
      when(memberRepository.findByWorkspace_IdAndUser_Id(1L, 50L))
          .thenReturn(Optional.of(existingMember));
      when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(invitationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      invitationService.acceptInvitation("token", 50L);

      assertThat(existingMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
      assertThat(existingMember.getRole()).isEqualTo(MemberRole.OPERATOR);
    }

    @Test
    @DisplayName("should_throw_conflict_when_already_active_member")
    void should_throw_conflict_when_already_active_member() {
      var workspace = new WorkspaceEntity();
      workspace.setId(1L);
      workspace.setName("WS");

      var inv = new WorkspaceInvitationEntity();
      inv.setStatus(InvitationStatus.PENDING);
      inv.setExpiresAt(OffsetDateTime.now().plusDays(3));
      inv.setWorkspace(workspace);

      when(invitationRepository.findByTokenHash(anyString()))
          .thenReturn(Optional.of(inv));

      var user = new AppUserEntity();
      user.setId(50L);
      when(appUserRepository.findById(50L)).thenReturn(Optional.of(user));

      var activeMember = new WorkspaceMemberEntity();
      activeMember.setStatus(MemberStatus.ACTIVE);
      when(memberRepository.findByWorkspace_IdAndUser_Id(1L, 50L))
          .thenReturn(Optional.of(activeMember));
      when(invitationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      assertThatThrownBy(() -> invitationService.acceptInvitation("token", 50L))
          .isInstanceOf(ConflictException.class)
          .hasMessage("invitation.already.member");
    }
  }

  @Nested
  @DisplayName("listInvitations")
  class ListInvitations {

    @Test
    @DisplayName("should_return_all_invitations_for_workspace")
    void should_return_all_invitations_for_workspace() {
      var inv1 = new WorkspaceInvitationEntity();
      inv1.setId(1L);
      inv1.setEmail("a@b.com");
      inv1.setRole(MemberRole.VIEWER);
      inv1.setStatus(InvitationStatus.PENDING);
      inv1.setExpiresAt(OffsetDateTime.now().plusDays(7));

      when(invitationRepository.findByWorkspace_IdOrderByCreatedAtDesc(5L))
          .thenReturn(List.of(inv1));

      List<WorkspaceInvitationEntity> result = invitationService.listInvitations(5L);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getEmail()).isEqualTo("a@b.com");
    }
  }
}
