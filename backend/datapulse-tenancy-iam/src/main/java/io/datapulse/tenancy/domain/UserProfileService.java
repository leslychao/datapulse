package io.datapulse.tenancy.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.tenancy.api.UpdateUserProfileRequest;
import io.datapulse.tenancy.api.UserProfileResponse;
import io.datapulse.tenancy.api.UserProfileResponse.MembershipResponse;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

  private final AppUserRepository appUserRepository;
  private final WorkspaceMemberRepository memberRepository;

  @Transactional(readOnly = true)
  public UserProfileResponse getProfile(Long userId) {
    AppUserEntity user = appUserRepository.findById(userId)
        .orElseThrow(() -> NotFoundException.entity("AppUser", userId));

    List<WorkspaceMemberEntity> memberships = memberRepository
        .findByUser_IdAndStatus(userId, MemberStatus.ACTIVE);

    List<MembershipResponse> membershipResponses = memberships.stream()
        .map(m -> new MembershipResponse(
            m.getWorkspace().getId(),
            m.getWorkspace().getName(),
            m.getWorkspace().getTenant().getId(),
            m.getWorkspace().getTenant().getName(),
            m.getRole().name()))
        .toList();

    boolean needsOnboarding = membershipResponses.isEmpty();

    return new UserProfileResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        needsOnboarding,
        membershipResponses);
  }

  @Transactional
  public UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request) {
    AppUserEntity user = appUserRepository.findById(userId)
        .orElseThrow(() -> NotFoundException.entity("AppUser", userId));

    user.setName(request.name().trim());
    appUserRepository.save(user);

    return getProfile(userId);
  }
}
