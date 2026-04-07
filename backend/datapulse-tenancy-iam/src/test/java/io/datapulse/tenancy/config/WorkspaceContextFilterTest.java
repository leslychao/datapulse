package io.datapulse.tenancy.config;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.MemberRole;
import io.datapulse.tenancy.domain.MemberStatus;
import io.datapulse.tenancy.domain.UserResolverService;
import io.datapulse.tenancy.domain.UserStatus;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceContextFilterTest {

  @Mock
  private UserResolverService userResolverService;
  @Mock
  private WorkspaceMemberRepository workspaceMemberRepository;
  @Mock
  private FilterChain filterChain;

  private WorkspaceContext workspaceContext;
  private WorkspaceContextFilter filter;

  @BeforeEach
  void setUp() {
    workspaceContext = new WorkspaceContext();
    filter = new WorkspaceContextFilter(
        userResolverService, workspaceMemberRepository,
        workspaceContext);
  }

  private Jwt buildJwt(String sub, String email) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(sub)
        .claim("email", email)
        .claim("preferred_username", "TestUser")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  private void setJwtAuth(Jwt jwt) {
    var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_openid")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Nested
  @DisplayName("doFilterInternal")
  class DoFilterInternal {

    @Test
    @DisplayName("should_pass_through_when_no_jwt_authentication")
    void should_pass_through_when_no_jwt_authentication() throws Exception {
      SecurityContextHolder.clearContext();
      var request = new MockHttpServletRequest();
      var response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      verify(userResolverService, never()).resolveOrProvision(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_provision_new_user_when_not_found_by_external_id")
    void should_provision_new_user_when_not_found_by_external_id() throws Exception {
      var jwt = buildJwt("ext-id-1", "new@test.com");
      setJwtAuth(jwt);

      var provisionedUser = new AppUserEntity();
      provisionedUser.setId(1L);
      provisionedUser.setStatus(UserStatus.ACTIVE);
      when(userResolverService.resolveOrProvision("ext-id-1", "new@test.com", "TestUser"))
          .thenReturn(provisionedUser);

      var request = new MockHttpServletRequest();
      var response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, filterChain);

      verify(userResolverService).resolveOrProvision("ext-id-1", "new@test.com", "TestUser");
      verify(filterChain).doFilter(request, response);
      assertThat(workspaceContext.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_return_403_when_user_is_deactivated")
    void should_return_403_when_user_is_deactivated() throws Exception {
      var jwt = buildJwt("ext-id-1", "user@test.com");
      setJwtAuth(jwt);

      var user = new AppUserEntity();
      user.setId(1L);
      user.setStatus(UserStatus.DEACTIVATED);
      when(userResolverService.resolveOrProvision("ext-id-1", "user@test.com", "TestUser"))
          .thenReturn(user);

      var request = new MockHttpServletRequest();
      var response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, filterChain);

      assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
      assertThat(response.getContentAsString()).contains("user.deactivated");
      verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("should_enrich_authorities_when_workspace_header_present_and_member_active")
    void should_enrich_authorities_when_workspace_header_present_and_member_active() throws Exception {
      var jwt = buildJwt("ext-id-1", "user@test.com");
      setJwtAuth(jwt);

      var user = new AppUserEntity();
      user.setId(1L);
      user.setStatus(UserStatus.ACTIVE);
      when(userResolverService.resolveOrProvision("ext-id-1", "user@test.com", "TestUser"))
          .thenReturn(user);

      var member = new WorkspaceMemberEntity();
      member.setRole(MemberRole.ADMIN);
      when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndStatus(
          5L, 1L, MemberStatus.ACTIVE))
          .thenReturn(Optional.of(member));

      var request = new MockHttpServletRequest();
      request.addHeader("X-Workspace-Id", "5");
      var response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, filterChain);

      assertThat(workspaceContext.getWorkspaceId()).isEqualTo(5L);
      assertThat(workspaceContext.getRole()).isEqualTo("ADMIN");

      var auth = SecurityContextHolder.getContext().getAuthentication();
      assertThat(auth.getAuthorities())
          .extracting("authority")
          .contains("ROLE_ADMIN");
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should_return_403_when_not_a_workspace_member")
    void should_return_403_when_not_a_workspace_member() throws Exception {
      var jwt = buildJwt("ext-id-1", "user@test.com");
      setJwtAuth(jwt);

      var user = new AppUserEntity();
      user.setId(1L);
      user.setStatus(UserStatus.ACTIVE);
      when(userResolverService.resolveOrProvision("ext-id-1", "user@test.com", "TestUser"))
          .thenReturn(user);
      when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndStatus(
          5L, 1L, MemberStatus.ACTIVE))
          .thenReturn(Optional.empty());

      var request = new MockHttpServletRequest();
      request.addHeader("X-Workspace-Id", "5");
      var response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, filterChain);

      assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
      assertThat(response.getContentAsString()).contains("workspace.membership.required");
    }

    @Test
    @DisplayName("should_return_400_when_workspace_header_not_a_number")
    void should_return_400_when_workspace_header_not_a_number() throws Exception {
      var jwt = buildJwt("ext-id-1", "user@test.com");
      setJwtAuth(jwt);

      var user = new AppUserEntity();
      user.setId(1L);
      user.setStatus(UserStatus.ACTIVE);
      when(userResolverService.resolveOrProvision("ext-id-1", "user@test.com", "TestUser"))
          .thenReturn(user);

      var request = new MockHttpServletRequest();
      request.addHeader("X-Workspace-Id", "not-a-number");
      var response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, filterChain);

      assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
      assertThat(response.getContentAsString()).contains("workspace.header.invalid");
    }

    @Test
    @DisplayName("should_proceed_without_workspace_context_when_no_header")
    void should_proceed_without_workspace_context_when_no_header() throws Exception {
      var jwt = buildJwt("ext-id-1", "user@test.com");
      setJwtAuth(jwt);

      var user = new AppUserEntity();
      user.setId(1L);
      user.setStatus(UserStatus.ACTIVE);
      when(userResolverService.resolveOrProvision("ext-id-1", "user@test.com", "TestUser"))
          .thenReturn(user);

      var request = new MockHttpServletRequest();
      var response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, filterChain);

      assertThat(workspaceContext.getUserId()).isEqualTo(1L);
      assertThat(workspaceContext.getWorkspaceId()).isNull();
      verify(filterChain).doFilter(request, response);
    }
  }
}
