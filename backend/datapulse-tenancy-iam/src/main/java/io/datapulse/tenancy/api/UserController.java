package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.UserProfile;
import io.datapulse.tenancy.domain.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserProfileService userProfileService;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/me")
    public UserProfileResponse getMe() {
        UserProfile profile = userProfileService.getProfile(workspaceContext.getUserId());
        return toResponse(profile);
    }

    @PutMapping("/me")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfile profile = userProfileService.updateProfile(
                workspaceContext.getUserId(), request.name());
        return toResponse(profile);
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        List<UserProfileResponse.MembershipResponse> memberships = profile.memberships().stream()
                .map(m -> new UserProfileResponse.MembershipResponse(
                        m.workspaceId(), m.workspaceName(),
                        m.tenantId(), m.tenantName(), m.role()))
                .toList();
        return new UserProfileResponse(
                profile.id(), profile.email(), profile.name(),
                profile.needsOnboarding(), memberships);
    }
}
