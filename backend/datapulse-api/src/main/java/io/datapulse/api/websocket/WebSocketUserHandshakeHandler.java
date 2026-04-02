package io.datapulse.api.websocket;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Binds the STOMP session {@link Principal#getName()} to the internal app user id so that
 * {@link org.springframework.messaging.simp.SimpMessagingTemplate#convertAndSendToUser} targets
 * match client subscriptions to {@code /user/queue/notifications}.
 */
@Component
public class WebSocketUserHandshakeHandler extends DefaultHandshakeHandler {

  @Override
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    Object raw = attributes.get(JwtHandshakeInterceptor.ATTR_USER_ID);
    if (raw instanceof Long userId) {
      return new UserIdPrincipal(userId);
    }
    return super.determineUser(request, wsHandler, attributes);
  }

  private record UserIdPrincipal(long userId) implements Principal {
    @Override
    public String getName() {
      return String.valueOf(userId);
    }
  }
}
