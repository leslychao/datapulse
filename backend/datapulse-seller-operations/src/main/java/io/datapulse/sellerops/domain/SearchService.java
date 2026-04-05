package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.SearchResultResponse;
import io.datapulse.sellerops.persistence.SearchReadRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

  private static final int SECONDARY_LIMIT = 5;

  private final SearchReadRepository searchReadRepository;

  public SearchResultResponse search(long workspaceId, String query, int limit) {
    if (query == null || query.isBlank() || query.length() < 2) {
      return new SearchResultResponse(List.of(), List.of(), List.of(), List.of());
    }

    String pattern = "%" + query.trim()
        .replace("%", "\\%")
        .replace("_", "\\_") + "%";

    return new SearchResultResponse(
        searchReadRepository.searchProducts(workspaceId, pattern, limit),
        searchReadRepository.searchPolicies(workspaceId, pattern, SECONDARY_LIMIT),
        searchReadRepository.searchPromos(workspaceId, pattern, SECONDARY_LIMIT),
        searchReadRepository.searchViews(workspaceId, pattern, SECONDARY_LIMIT));
  }
}
