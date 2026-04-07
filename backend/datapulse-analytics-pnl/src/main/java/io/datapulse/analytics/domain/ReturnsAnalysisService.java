package io.datapulse.analytics.domain;

import java.util.List;

import io.datapulse.analytics.api.ProductReturnResponse;
import io.datapulse.analytics.api.ReturnsFilter;
import io.datapulse.analytics.api.ReturnsSummaryResponse;
import io.datapulse.analytics.api.ReturnsTrendResponse;
import io.datapulse.analytics.persistence.ReturnsReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnsAnalysisService {

  private final ReturnsReadRepository returnsReadRepository;

  public List<ReturnsSummaryResponse> getSummary(long workspaceId, ReturnsFilter filter) {
    return returnsReadRepository.findSummary(workspaceId, filter);
  }

  public Page<ProductReturnResponse> getByProduct(long workspaceId, ReturnsFilter filter,
      Pageable pageable) {
    String sortColumn = pageable.getSort().isSorted()
        ? pageable.getSort().iterator().next().getProperty()
        : "returnRatePct";

    List<ProductReturnResponse> content = returnsReadRepository.findByProduct(
        workspaceId, filter, sortColumn, pageable.getPageSize(), pageable.getOffset());
    long total = returnsReadRepository.countByProduct(workspaceId, filter);

    return new PageImpl<>(content, pageable, total);
  }

  public List<ReturnsTrendResponse> getTrend(long workspaceId, ReturnsFilter filter) {
    return returnsReadRepository.findTrend(workspaceId, filter);
  }
}
