package io.datapulse.audit.domain;

import java.util.List;

import io.datapulse.audit.api.AuditLogFilter;
import io.datapulse.audit.api.AuditLogResponse;
import io.datapulse.audit.persistence.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listAuditLog(long workspaceId, AuditLogFilter filter,
                                                Pageable pageable) {
        String sortColumn = pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next().getProperty()
                : "createdAt";

        List<AuditLogResponse> content = auditLogRepository.findAll(
                workspaceId, filter, sortColumn,
                pageable.getPageSize(), pageable.getOffset());

        long total = auditLogRepository.count(workspaceId, filter);

        return new PageImpl<>(content, pageable, total);
    }
}
