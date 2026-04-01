package io.datapulse.sellerops.api;

import io.datapulse.sellerops.domain.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/search",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public SearchResultResponse search(
      @PathVariable("workspaceId") long workspaceId,
      @RequestParam("q") String query,
      @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return searchService.search(workspaceId, query, Math.min(limit, 50));
  }
}
