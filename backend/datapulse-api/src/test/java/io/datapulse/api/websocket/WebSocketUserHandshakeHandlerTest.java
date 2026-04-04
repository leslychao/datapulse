package io.datapulse.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

class WebSocketUserHandshakeHandlerTest {

  @Test
  void determineUser_returnsPrincipalWithStringUserId_whenAttributePresent() {
    TestHandshakeHandler handler = new TestHandshakeHandler();
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(JwtHandshakeInterceptor.ATTR_USER_ID, 42L);

    Principal principal = handler.unwrapDetermineUser(null, null, attributes);

    assertThat(principal).isNotNull();
    assertThat(principal.getName()).isEqualTo("42");
  }

  @Test
  void determineUser_delegatesToDefault_whenUserIdAttributeMissing() {
    TestHandshakeHandler handler = new TestHandshakeHandler();
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getPrincipal()).thenReturn(null);

    Principal principal = handler.unwrapDetermineUser(request, null, Map.of());

    assertThat(principal).isNull();
  }

  /** Exposes {@code determineUser} for tests (protected in {@link WebSocketUserHandshakeHandler}). */
  private static final class TestHandshakeHandler extends WebSocketUserHandshakeHandler {

    Principal unwrapDetermineUser(
        ServerHttpRequest request,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes) {
      return determineUser(request, wsHandler, attributes);
    }
  }
}
