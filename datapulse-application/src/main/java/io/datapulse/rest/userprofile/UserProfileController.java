package io.datapulse.rest.userprofile;

import io.datapulse.core.service.account.UserProfileProvisioningService;
import io.datapulse.core.service.account.UserProfileService;
import io.datapulse.domain.dto.request.UserProfileCreateRequest;
import io.datapulse.domain.dto.request.UserProfileUpdateRequest;
import io.datapulse.domain.dto.response.UserProfileResponse;
import io.datapulse.domain.dto.response.me.MeResponse;
import io.datapulse.domain.dto.security.AuthenticatedUser;
import io.datapulse.security.identity.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/user-profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class UserProfileController {

  private final UserProfileService userProfileService;
  private final CurrentUserProvider currentUserProvider;
  private final UserProfileProvisioningService userProfileProvisioningService;

  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public UserProfileResponse create(@RequestBody UserProfileCreateRequest request) {
    return userProfileService.createFromRequest(request);
  }

  @PutMapping(path = "/{id}", consumes = "application/json")
  public UserProfileResponse update(
      @PathVariable Long id,
      @RequestBody UserProfileUpdateRequest request
  ) {
    return userProfileService.updateFromRequest(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    userProfileService.delete(id);
  }

  @GetMapping("/me")
  public MeResponse me() {
    AuthenticatedUser currentUser = currentUserProvider.currentUser();
    userProfileProvisioningService.ensureUserProfile(currentUser);
    return MeResponse.from(currentUser);
  }
}
