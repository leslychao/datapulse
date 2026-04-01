package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.SearchResultResponse;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {

  private final GridPostgresReadRepository gridPostgresReadRepository;

  public List<SearchResultResponse> search(long workspaceId, String query, int limit) {
    if (query == null || query.isBlank() || query.length() < 2) {
      return List.of();
    }
    return gridPostgresReadRepository.search(workspaceId, query.trim(), limit);
  }
}
