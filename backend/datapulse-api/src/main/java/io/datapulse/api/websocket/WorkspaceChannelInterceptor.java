package io.datapulse.api.websocket;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkspaceChannelInterceptor implements ChannelInterceptor {

    private static final Pattern WORKSPACE_TOPIC_PATTERN =
            Pattern.compile("^/topic/workspace/(\\d+)/.*$");

    @Override
    @SuppressWarnings("unchecked") // safe: attributes populated by JwtHandshakeInterceptor with Set<Long>
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SUBSCRIBE != accessor.getCommand()) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = WORKSPACE_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        long workspaceId;
        try {
            workspaceId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new MessageDeliveryException("Invalid workspace id in destination: " + destination);
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new MessageDeliveryException("No session attributes — handshake may have failed");
        }

        Set<Long> allowedWorkspaces =
                (Set<Long>) sessionAttributes.get(JwtHandshakeInterceptor.ATTR_WORKSPACE_IDS);
        if (allowedWorkspaces == null || !allowedWorkspaces.contains(workspaceId)) {
            log.warn("WebSocket subscription denied: workspaceId={}, destination={}",
                    workspaceId, destination);
            throw new MessageDeliveryException(
                    "Not authorized to subscribe to workspace " + workspaceId);
        }

        return message;
    }
}
