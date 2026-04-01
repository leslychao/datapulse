package io.datapulse.sellerops.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.sellerops.domain.MismatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces/{workspaceId}/mismatches",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MismatchController {

  private final MismatchService mismatchService;
  private final WorkspaceContext workspaceContext;

  @GetMapping("/summary")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public MismatchSummaryResponse getSummary(
      @PathVariable("workspaceId") Long workspaceId) {
    return mismatchService.getSummary(workspaceId);
  }

  @GetMapping("/{mismatchId}")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public MismatchDetailResponse getMismatch(
      @PathVariable("workspaceId") Long workspaceId,
      @PathVariable("mismatchId") Long mismatchId) {
    return mismatchService.getMismatchDetail(workspaceId, mismatchId);
  }

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<MismatchResponse> listMismatches(
      @PathVariable("workspaceId") Long workspaceId,
      MismatchFilter filter,
      @PageableDefault(size = 20, sort = "detectedAt",
          direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
    return mismatchService.listMismatches(workspaceId, filter, pageable);
  }

  @PostMapping("/{mismatchId}/acknowledge")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public MismatchResponse acknowledge(
      @PathVariable("workspaceId") Long workspaceId,
      @PathVariable("mismatchId") Long mismatchId) {
    return mismatchService.acknowledge(
        workspaceId, mismatchId, workspaceContext.getUserId());
  }

  @PostMapping("/{mismatchId}/resolve")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public MismatchResponse resolve(
      @PathVariable("workspaceId") Long workspaceId,
      @PathVariable("mismatchId") Long mismatchId,
      @Valid @RequestBody ResolveMismatchRequest request) {
    return mismatchService.resolve(
        workspaceId, mismatchId, request.resolution(), request.note());
  }
}
