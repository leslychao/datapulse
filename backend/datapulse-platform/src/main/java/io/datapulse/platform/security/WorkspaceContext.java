package io.datapulse.platform.security;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Data
@Component
@RequestScope
public class WorkspaceContext {

    private Long userId;
    private Long workspaceId;
    private String role;
}
