package io.datapulse.platform.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Getter
@Setter
@Component
@RequestScope
public class WorkspaceContext {

    private Long userId;
    private Long workspaceId;
    private String role;
}
