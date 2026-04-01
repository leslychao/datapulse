package io.datapulse.audit.api;

import io.datapulse.audit.domain.AuditLogService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/audit-log", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
    public Page<AuditLogResponse> listAuditLog(AuditLogFilter filter, Pageable pageable) {
        return auditLogService.listAuditLog(workspaceContext.getWorkspaceId(), filter, pageable);
    }
}
