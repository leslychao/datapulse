package io.datapulse.api.websocket;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.datapulse.tenancy.domain.MemberStatus;
import io.datapulse.tenancy.domain.UserResolverService;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberEntity;
import io.datapulse.tenancy.persistence.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "ws.userId";
    static final String ATTR_WORKSPACE_IDS = "ws.workspaceIds";

    private final JwtDecoder jwtDecoder;
    private final UserResolverService userResolverService;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            log.debug("WebSocket handshake rejected: no token found");
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String sub = jwt.getSubject();

            AppUserEntity user = userResolverService.resolve(sub);
            if (user == null) {
                log.debug("WebSocket handshake rejected: unknown user sub={}", sub);
                return false;
            }

            Set<Long> workspaceIds = workspaceMemberRepository
                    .findByUser_IdAndStatus(user.getId(), MemberStatus.ACTIVE)
                    .stream()
                    .map(WorkspaceMemberEntity::getWorkspace)
                    .map(w -> w.getId())
                    .collect(Collectors.toSet());

            attributes.put(ATTR_USER_ID, user.getId());
            attributes.put(ATTR_WORKSPACE_IDS, workspaceIds);

            log.debug("WebSocket handshake accepted: userId={}, workspaces={}",
                    user.getId(), workspaceIds.size());
            return true;
        } catch (JwtException e) {
            log.debug("WebSocket handshake rejected: invalid JWT, error={}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter("token");
        }
        return null;
    }
}
