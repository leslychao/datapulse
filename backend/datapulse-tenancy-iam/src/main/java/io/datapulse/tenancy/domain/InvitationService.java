package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.api.CreateInvitationRequest;
import io.datapulse.tenancy.api.InvitationResponse;
import io.datapulse.tenancy.persistence.WorkspaceEntity;
import io.datapulse.tenancy.persistence.WorkspaceInvitationEntity;
import io.datapulse.tenancy.persistence.WorkspaceInvitationRepository;
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
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<InvitationResponse> listInvitations(Long workspaceId) {
        return invitationRepository.findByWorkspace_IdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InvitationResponse createInvitation(Long workspaceId, Long invitedByUserId,
                                               String actorRole, CreateInvitationRequest request) {
        if (request.role() == MemberRole.OWNER) {
            throw BadRequestException.of("invitation.cannot.assign.owner");
        }

        MemberRole actorMemberRole = MemberRole.valueOf(actorRole);
        if (actorMemberRole == MemberRole.ADMIN && request.role() == MemberRole.ADMIN) {
            throw BadRequestException.of("invitation.admin.cannot.invite.admin");
        }

        boolean alreadyMember = memberRepository.existsByWorkspace_IdAndUser_IdAndStatus(
                workspaceId, null, MemberStatus.ACTIVE);
        // Check by email instead — need to check if email already has active membership
        // For now, we check for duplicate pending invitations

        Optional<WorkspaceInvitationEntity> existing = invitationRepository
                .findByWorkspace_IdAndEmailAndStatus(workspaceId, request.email(), InvitationStatus.PENDING);

        if (existing.isPresent()) {
            WorkspaceInvitationEntity inv = existing.get();
            inv.setRole(request.role());
            inv.setTokenHash(hashToken(generateToken()));
            inv.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRATION_DAYS));
            invitationRepository.save(inv);
            return toResponse(inv);
        }

        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> NotFoundException.workspace(workspaceId));

        String token = generateToken();
        WorkspaceInvitationEntity invitation = new WorkspaceInvitationEntity();
        invitation.setWorkspace(workspace);
        invitation.setEmail(request.email().trim().toLowerCase());
        invitation.setRole(request.role());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setTokenHash(hashToken(token));
        invitation.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRATION_DAYS));
        invitation.setInvitedByUserId(invitedByUserId);

        invitationRepository.save(invitation);
        return toResponse(invitation);
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
    }

    @Transactional
    public InvitationResponse resendInvitation(Long workspaceId, Long invitationId) {
        WorkspaceInvitationEntity invitation = invitationRepository
                .findByIdAndWorkspace_Id(invitationId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("WorkspaceInvitation", invitationId));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw BadRequestException.of("invitation.not.pending");
        }

        invitation.setTokenHash(hashToken(generateToken()));
        invitation.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRATION_DAYS));
        invitationRepository.save(invitation);
        return toResponse(invitation);
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

    private InvitationResponse toResponse(WorkspaceInvitationEntity entity) {
        return new InvitationResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }
}
