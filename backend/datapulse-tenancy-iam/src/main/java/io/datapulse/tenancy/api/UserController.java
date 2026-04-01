package io.datapulse.tenancy.api;

import io.datapulse.platform.security.WorkspaceContext;
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

@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

  private final UserProfileService userProfileService;
  private final WorkspaceContext workspaceContext;

  @GetMapping("/me")
  public UserProfileResponse getMe() {
    return userProfileService.getProfile(workspaceContext.getUserId());
  }

  @PutMapping("/me")
  public UserProfileResponse updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
    return userProfileService.updateProfile(workspaceContext.getUserId(), request);
  }
}
