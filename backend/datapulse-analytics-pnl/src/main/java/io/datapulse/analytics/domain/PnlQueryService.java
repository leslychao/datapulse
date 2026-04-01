package io.datapulse.analytics.domain;

import java.util.List;

import io.datapulse.analytics.api.PnlFilter;
import io.datapulse.analytics.api.PnlSummaryResponse;
import io.datapulse.analytics.api.PnlTrendResponse;
import io.datapulse.analytics.api.PostingDetailResponse;
import io.datapulse.analytics.api.PostingPnlResponse;
import io.datapulse.analytics.api.ProductPnlResponse;
import io.datapulse.analytics.api.TrendGranularity;
import io.datapulse.analytics.persistence.PnlReadRepository;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PnlQueryService {

    private final PnlReadRepository pnlReadRepository;
    private final WorkspaceConnectionRepository connectionRepository;

    public List<PnlSummaryResponse> getSummary(long workspaceId, PnlFilter filter) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return pnlReadRepository.findSummary(connectionIds, filter);
    }

    public Page<ProductPnlResponse> getByProduct(long workspaceId, PnlFilter filter, Pageable pageable) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        String sortColumn = extractSortColumn(pageable, "revenueAmount");
        List<ProductPnlResponse> content = pnlReadRepository.findByProduct(
                connectionIds, filter, sortColumn, pageable.getPageSize(), pageable.getOffset());
        long total = pnlReadRepository.countByProduct(connectionIds, filter);

        return new PageImpl<>(content, pageable, total);
    }

    public Page<PostingPnlResponse> getByPosting(long workspaceId, PnlFilter filter, Pageable pageable) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        String sortColumn = extractSortColumn(pageable, "financeDate");
        List<PostingPnlResponse> content = pnlReadRepository.findByPosting(
                connectionIds, filter, sortColumn, pageable.getPageSize(), pageable.getOffset());
        long total = pnlReadRepository.countByPosting(connectionIds, filter);

        return new PageImpl<>(content, pageable, total);
    }

    public List<PostingDetailResponse> getPostingDetails(long workspaceId, String postingId) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return pnlReadRepository.findPostingDetails(connectionIds, postingId);
    }

    public List<PnlTrendResponse> getTrend(long workspaceId, PnlFilter filter,
                                            TrendGranularity granularity) {
        List<Long> connectionIds = resolveConnectionIds(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return pnlReadRepository.findTrend(connectionIds, filter, granularity);
    }

    private List<Long> resolveConnectionIds(long workspaceId) {
        return connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
    }

    private String extractSortColumn(Pageable pageable, String defaultColumn) {
        if (pageable.getSort().isSorted()) {
            return pageable.getSort().iterator().next().getProperty();
        }
        return defaultColumn;
    }
}
