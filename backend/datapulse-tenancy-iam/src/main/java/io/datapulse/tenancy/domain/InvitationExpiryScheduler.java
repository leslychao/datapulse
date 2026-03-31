package io.datapulse.tenancy.domain;

import io.datapulse.tenancy.persistence.WorkspaceInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationExpiryScheduler {

    private final WorkspaceInvitationRepository invitationRepository;

    @Scheduled(cron = "${datapulse.tenancy.invitation-expiry-cron:0 0 * * * *}")
    @Transactional
    public void expireOverdueInvitations() {
        try {
            int expired = invitationRepository.expirePendingInvitations(
                    InvitationStatus.PENDING,
                    InvitationStatus.EXPIRED,
                    OffsetDateTime.now());
            if (expired > 0) {
                log.info("Expired overdue invitations: count={}", expired);
            }
        } catch (Exception e) {
            log.error("Failed to expire invitations: error={}", e.getMessage(), e);
        }
    }
}
