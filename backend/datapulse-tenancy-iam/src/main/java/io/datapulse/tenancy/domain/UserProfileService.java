package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final AppUserRepository appUserRepository;
    private final WorkspaceMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public UserProfile getProfile(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.entity("AppUser", userId));

        List<WorkspaceMemberEntity> memberships = memberRepository
                .findByUser_IdAndStatus(userId, MemberStatus.ACTIVE);

        List<UserProfile.WorkspaceMembership> membershipList = memberships.stream()
                .map(m -> new UserProfile.WorkspaceMembership(
                        m.getWorkspace().getId(),
                        m.getWorkspace().getName(),
                        m.getWorkspace().getTenant().getId(),
                        m.getWorkspace().getTenant().getName(),
                        m.getRole().name()))
                .toList();

        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getName(),
                membershipList.isEmpty(),
                membershipList);
    }

    @Transactional
    public UserProfile updateProfile(Long userId, String name) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.entity("AppUser", userId));

        user.setName(name.trim());
        appUserRepository.save(user);

        return getProfile(userId);
    }
}
