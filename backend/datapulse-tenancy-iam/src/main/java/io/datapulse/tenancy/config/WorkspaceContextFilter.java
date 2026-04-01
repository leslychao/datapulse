package io.datapulse.tenancy.config;

import io.datapulse.platform.audit.AuditEvent;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.tenancy.domain.MemberStatus;
import io.datapulse.tenancy.domain.UserStatus;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceContextFilter extends OncePerRequestFilter {

    private static final String WORKSPACE_HEADER = "X-Workspace-Id";

    private final AppUserRepository appUserRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceContext workspaceContext;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("WorkspaceContextFilter: path={}, authType={}, authName={}",
            request.getRequestURI(),
            authentication != null ? authentication.getClass().getSimpleName() : "null",
            authentication != null ? authentication.getName() : "null");

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sub = jwtAuth.getToken().getSubject();
        String email = jwtAuth.getToken().getClaimAsString("email");
        String name = resolveDisplayName(jwtAuth);

        AppUserEntity user = resolveOrProvisionUser(sub, email, name);
        log.debug("WorkspaceContextFilter: resolved user id={}, email={}", user.getId(), user.getEmail());

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            sendError(response, HttpStatus.FORBIDDEN, "user.deactivated");
            return;
        }

        workspaceContext.setUserId(user.getId());

        String workspaceIdHeader = request.getHeader(WORKSPACE_HEADER);
        if (workspaceIdHeader != null) {
            long workspaceId;
            try {
                workspaceId = Long.parseLong(workspaceIdHeader);
            } catch (NumberFormatException e) {
                sendError(response, HttpStatus.BAD_REQUEST, "workspace.header.invalid");
                return;
            }

            Optional<WorkspaceMemberEntity> membership = workspaceMemberRepository
                    .findByWorkspace_IdAndUser_IdAndStatus(workspaceId, user.getId(), MemberStatus.ACTIVE);

            if (membership.isEmpty()) {
                sendError(response, HttpStatus.FORBIDDEN, "workspace.membership.required");
                return;
            }

            WorkspaceMemberEntity member = membership.get();
            workspaceContext.setWorkspaceId(workspaceId);
            workspaceContext.setRole(member.getRole().name());

            Collection<GrantedAuthority> enrichedAuthorities = new ArrayList<>(jwtAuth.getAuthorities());
            enrichedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
            JwtAuthenticationToken enrichedAuth = new JwtAuthenticationToken(
                    jwtAuth.getToken(), enrichedAuthorities, jwtAuth.getName());
            SecurityContextHolder.getContext().setAuthentication(enrichedAuth);
        }

        filterChain.doFilter(request, response);
    }

    private AppUserEntity resolveOrProvisionUser(String sub, String email, String name) {
        return appUserRepository.findByExternalId(sub)
                .orElseGet(() -> provisionUser(sub, email, name));
    }

    private AppUserEntity provisionUser(String sub, String email, String name) {
        log.info("Auto-provisioning user: externalId={}, email={}", sub, email);

        var user = new AppUserEntity();
        user.setExternalId(sub);
        user.setEmail(email);
        user.setName(name != null ? name : email);
        user.setStatus(UserStatus.ACTIVE);
        AppUserEntity saved = appUserRepository.save(user);

        eventPublisher.publishEvent(new AuditEvent(
                0L, "SYSTEM", saved.getId(), "user.provision",
                "app_user", String.valueOf(saved.getId()),
                "SUCCESS", null, null, null));

        return saved;
    }

    private String resolveDisplayName(JwtAuthenticationToken jwtAuth) {
        String preferredUsername = jwtAuth.getToken().getClaimAsString("preferred_username");
        if (preferredUsername != null) {
            return preferredUsername;
        }
        String givenName = jwtAuth.getToken().getClaimAsString("given_name");
        String familyName = jwtAuth.getToken().getClaimAsString("family_name");
        if (givenName != null || familyName != null) {
            return ((givenName != null ? givenName : "") + " " + (familyName != null ? familyName : "")).trim();
        }
        return jwtAuth.getToken().getClaimAsString("name");
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String messageKey) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":%d,\"error\":\"%s\",\"messageKey\":\"%s\"}"
                        .formatted(status.value(), status.getReasonPhrase(), messageKey)
        );
    }
}
