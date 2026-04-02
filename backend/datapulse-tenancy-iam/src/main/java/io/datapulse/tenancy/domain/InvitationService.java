package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.AppException;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceInvitationEntity;
import io.datapulse.tenancy.persistence.WorkspaceInvitationRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRATION_DAYS = 7;

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AppUserRepository appUserRepository;
    private final Optional<InvitationMailService> mailService;
    private final TenancyAuditPublisher auditPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationEntity> listInvitations(Long workspaceId) {
        return invitationRepository.findByWorkspace_IdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional
    public WorkspaceInvitationEntity createInvitation(Long workspaceId, Long invitedByUserId,
                                                      String actorRole, String email,
                                                      MemberRole role) {
        if (role == MemberRole.OWNER) {
            throw BadRequestException.of("invitation.cannot.assign.owner");
        }

        MemberRole actorMemberRole = MemberRole.valueOf(actorRole);
        if (actorMemberRole == MemberRole.ADMIN && role == MemberRole.ADMIN) {
            throw BadRequestException.of("invitation.admin.cannot.invite.admin");
        }

        String normalizedEmail = email.trim().toLowerCase();

        appUserRepository.findByEmail(normalizedEmail)
                .ifPresent(existingUser -> {
                    if (memberRepository.existsByWorkspace_IdAndUser_IdAndStatus(
                            workspaceId, existingUser.getId(), MemberStatus.ACTIVE)) {
                        throw ConflictException.of("invitation.user.already.member");
                    }
                });

        Optional<WorkspaceInvitationEntity> existing = invitationRepository
                .findByWorkspace_IdAndEmailAndStatus(workspaceId, normalizedEmail, InvitationStatus.PENDING);

        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));

        String rawToken = generateToken();

        if (existing.isPresent()) {
            WorkspaceInvitationEntity inv = existing.get();
            inv.setRole(role);
            inv.setTokenHash(hashToken(rawToken));
            inv.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRATION_DAYS));
            invitationRepository.save(inv);
            sendInvitationEmail(inv, rawToken, invitedByUserId, workspace.getName());
            return inv;
        }

        WorkspaceInvitationEntity invitation = new WorkspaceInvitationEntity();
        invitation.setWorkspace(workspace);
        invitation.setEmail(normalizedEmail);
        invitation.setRole(role);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setTokenHash(hashToken(rawToken));
        invitation.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRATION_DAYS));
        invitation.setInvitedByUserId(invitedByUserId);

        invitationRepository.save(invitation);
        auditPublisher.publish("member.invite", "workspace_invitation",
                String.valueOf(invitation.getId()));
        sendInvitationEmail(invitation, rawToken, invitedByUserId, workspace.getName());
        return invitation;
    }

    @Transactional
    public void cancelInvitation(Long workspaceId, Long invitationId) {
        WorkspaceInvitationEntity invitation = invitationRepository
                .findByIdAndWorkspace_Id(invitationId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("WorkspaceInvitation", invitationId));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw BadRequestException.of("invitation.not.pending");
        }

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);
        auditPublisher.publish("member.cancel_invitation", "workspace_invitation",
                String.valueOf(invitationId));
    }

    @Transactional
    public WorkspaceInvitationEntity resendInvitation(Long workspaceId, Long invitationId) {
        WorkspaceInvitationEntity invitation = invitationRepository
                .findByIdAndWorkspace_Id(invitationId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("WorkspaceInvitation", invitationId));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw BadRequestException.of("invitation.not.pending");
        }

        String rawToken = generateToken();
        invitation.setTokenHash(hashToken(rawToken));
        invitation.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRATION_DAYS));
        invitationRepository.save(invitation);

        sendInvitationEmail(invitation, rawToken, invitation.getInvitedByUserId(),
                invitation.getWorkspace().getName());

        auditPublisher.publish("member.resend_invitation", "workspace_invitation",
                String.valueOf(invitationId));

        return invitation;
    }

    @Transactional
    public WorkspaceInvitationEntity acceptInvitation(String rawToken, Long acceptingUserId) {
        String tokenHash = hashToken(rawToken);

        WorkspaceInvitationEntity invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> NotFoundException.of("invitation.not.found"));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw ConflictException.of("invitation.already.accepted");
        }
        if (invitation.getStatus() == InvitationStatus.EXPIRED
                || invitation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new AppException("invitation.expired", 410);
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw BadRequestException.of("invitation.not.pending");
        }

        AppUserEntity user = appUserRepository.findById(acceptingUserId)
                .orElseThrow(() -> NotFoundException.entity("AppUser", acceptingUserId));

        WorkspaceEntity workspace = invitation.getWorkspace();

        Optional<WorkspaceMemberEntity> existingMembership = memberRepository
                .findByWorkspace_IdAndUser_Id(workspace.getId(), acceptingUserId);

        if (existingMembership.isPresent()) {
            WorkspaceMemberEntity existing = existingMembership.get();
            if (existing.getStatus() == MemberStatus.ACTIVE) {
                invitation.setStatus(InvitationStatus.ACCEPTED);
                invitation.setAcceptedByUserId(acceptingUserId);
                invitationRepository.save(invitation);
                throw ConflictException.of("invitation.already.member");
            }
            existing.setStatus(MemberStatus.ACTIVE);
            existing.setRole(invitation.getRole());
            memberRepository.save(existing);
        } else {
            var member = new WorkspaceMemberEntity();
            member.setWorkspace(workspace);
            member.setUser(user);
            member.setRole(invitation.getRole());
            member.setStatus(MemberStatus.ACTIVE);
            memberRepository.save(member);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedByUserId(acceptingUserId);
        invitationRepository.save(invitation);
        auditPublisher.publish("member.accept_invitation", "workspace_invitation",
                String.valueOf(invitation.getId()));

        return invitation;
    }

    private void sendInvitationEmail(WorkspaceInvitationEntity invitation, String rawToken,
                                      Long inviterUserId, String workspaceName) {
        mailService.ifPresent(ms -> {
            String inviterName = appUserRepository.findById(inviterUserId)
                    .map(AppUserEntity::getName)
                    .orElse("Коллега");
            ms.sendInvitationEmail(invitation.getEmail(), inviterName,
                    workspaceName, rawToken, EXPIRATION_DAYS);
        });
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
