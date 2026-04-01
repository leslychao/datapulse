package io.datapulse.analytics.domain;

import java.util.List;

import io.datapulse.analytics.api.ProductReturnResponse;
import io.datapulse.analytics.api.ReturnsFilter;
import io.datapulse.analytics.api.ReturnsSummaryResponse;
import io.datapulse.analytics.api.ReturnsTrendResponse;
import io.datapulse.analytics.persistence.ReturnsReadRepository;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReturnsAnalysisService {

    private final ReturnsReadRepository returnsReadRepository;
    private final WorkspaceConnectionRepository connectionRepository;

    public List<ReturnsSummaryResponse> getSummary(long workspaceId, ReturnsFilter filter) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return returnsReadRepository.findSummary(connectionIds, filter);
    }

    public Page<ProductReturnResponse> getByProduct(long workspaceId, ReturnsFilter filter,
                                                     Pageable pageable) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        String sortColumn = pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next().getProperty()
                : "returnRatePct";

        List<ProductReturnResponse> content = returnsReadRepository.findByProduct(
                connectionIds, filter, sortColumn, pageable.getPageSize(), pageable.getOffset());
        long total = returnsReadRepository.countByProduct(connectionIds, filter);

        return new PageImpl<>(content, pageable, total);
    }

    public List<ReturnsTrendResponse> getTrend(long workspaceId, ReturnsFilter filter) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return returnsReadRepository.findTrend(connectionIds, filter);
    }

    private List<Long> resolveConnectionIds(long workspaceId) {
        return connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
    }
}
