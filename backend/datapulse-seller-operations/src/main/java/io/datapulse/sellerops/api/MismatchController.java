package io.datapulse.sellerops.api;

import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.sellerops.domain.MismatchService;
import io.datapulse.sellerops.domain.MismatchStatus;
import io.datapulse.sellerops.persistence.MismatchRow;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

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
      @PageableDefault(size = 50, sort = "detectedAt",
          direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
    return mismatchService.listMismatches(workspaceId, filter.normalize(), pageable);
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
        workspaceId, mismatchId, request.resolution(), request.note(),
        workspaceContext.getUserId());
  }

  @PostMapping("/bulk-ignore")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public BulkIgnoreResponse bulkIgnore(
      @PathVariable("workspaceId") Long workspaceId,
      @Valid @RequestBody BulkIgnoreRequest request) {
    int updated = mismatchService.bulkIgnore(
        workspaceId, request.ids(), request.reason(), workspaceContext.getUserId());
    return new BulkIgnoreResponse(updated);
  }

  @GetMapping(value = "/export", produces = "text/csv")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public void exportCsv(
      @PathVariable("workspaceId") Long workspaceId,
      MismatchFilter filter,
      HttpServletResponse response) throws IOException {
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=mismatches.csv");

    List<MismatchRow> rows = mismatchService.findAllForExport(workspaceId, filter.normalize());

    try (PrintWriter writer = response.getWriter()) {
      writer.println("ID,Type,Offer,SKU,Marketplace,Expected,Actual,Delta%,Severity,Status,Detected");
      for (MismatchRow row : rows) {
        String apiStatus = MismatchStatus.toApi(row.getStatus(), row.getResolvedReason());
        writer.printf("%d,%s,\"%s\",\"%s\",%s,\"%s\",\"%s\",%s,%s,%s,%s%n",
            row.getAlertEventId(),
            row.getMismatchType(),
            escapeCsv(row.getOfferName()),
            escapeCsv(row.getSkuCode()),
            row.getMarketplaceType() != null ? row.getMarketplaceType() : "",
            escapeCsv(row.getExpectedValue()),
            escapeCsv(row.getActualValue()),
            row.getDeltaPct() != null ? row.getDeltaPct().toPlainString() : "",
            row.getSeverity(),
            apiStatus,
            row.getDetectedAt() != null ? row.getDetectedAt().toString() : "");
      }
    }
  }

  private static String escapeCsv(String s) {
    if (s == null) return "";
    return s.replace("\"", "\"\"");
  }

  public record BulkIgnoreRequest(
      @jakarta.validation.constraints.NotEmpty List<Long> ids,
      @jakarta.validation.constraints.NotBlank String reason
  ) {
  }

  public record BulkIgnoreResponse(int updated) {
  }
}
