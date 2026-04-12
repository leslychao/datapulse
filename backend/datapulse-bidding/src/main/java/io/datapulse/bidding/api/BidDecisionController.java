package io.datapulse.bidding.api;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import io.datapulse.bidding.domain.BidDecisionQueryService;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/workspaces/{workspaceId}/bidding/decisions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BidDecisionController {

  private final BidDecisionQueryService decisionQueryService;
  private final BidPolicyMapper mapper;

  @GetMapping
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public Page<BidDecisionSummaryResponse> listDecisions(
      @PathVariable("workspaceId") long workspaceId,
      @RequestParam(value = "bidPolicyId", required = false) Long bidPolicyId,
      @RequestParam(value = "biddingRunId", required = false) Long biddingRunId,
      @RequestParam(value = "marketplaceOfferId", required = false) Long marketplaceOfferId,
      Pageable pageable) {

    return decisionQueryService.listDecisions(
            workspaceId, bidPolicyId, biddingRunId, marketplaceOfferId, pageable)
        .map(mapper::toSummary);
  }

  @GetMapping("/{id}")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public BidDecisionDetailResponse getDecision(
      @PathVariable("workspaceId") long workspaceId,
      @PathVariable("id") long id) {

    BidDecisionEntity entity = decisionQueryService.getDecision(id);
    return mapper.toDetail(entity);
  }

  @GetMapping(value = "/export", produces = "text/csv")
  @PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId)")
  public void exportCsv(
      @PathVariable("workspaceId") long workspaceId,
      @RequestParam(value = "bidPolicyId", required = false) Long bidPolicyId,
      @RequestParam(value = "dateFrom", required = false) String dateFrom,
      @RequestParam(value = "dateTo", required = false) String dateTo,
      HttpServletResponse response) throws Exception {

    String filename = "bid-decisions-%s.csv".formatted(
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader("Content-Disposition",
        "attachment; filename=\"%s\"".formatted(filename));
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    var writer = new PrintWriter(new OutputStreamWriter(
        response.getOutputStream(), StandardCharsets.UTF_8));
    writer.write('\uFEFF');
    writer.println("ID,MarketplaceOfferId,Strategy,DecisionType,"
        + "CurrentBid,TargetBid,Explanation,ExecutionMode,CreatedAt");

    int page = 0;
    int size = 500;
    Page<BidDecisionEntity> batch;
    do {
      Pageable pageable = PageRequest.of(page, size,
          Sort.by(Sort.Direction.DESC, "createdAt"));
      batch = decisionQueryService.listDecisions(
          workspaceId, bidPolicyId, null, null, pageable);

      for (BidDecisionEntity d : batch.getContent()) {
        writer.printf("%d,%d,%s,%s,%s,%s,\"%s\",%s,%s%n",
            d.getId(),
            d.getMarketplaceOfferId(),
            d.getStrategyType(),
            d.getDecisionType(),
            d.getCurrentBid() != null ? d.getCurrentBid() : "",
            d.getTargetBid() != null ? d.getTargetBid() : "",
            escapeCsv(d.getExplanationSummary()),
            d.getExecutionMode(),
            d.getCreatedAt());
      }
      page++;
    } while (batch.hasNext());

    writer.flush();
  }

  private static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\"", "\"\"");
  }
}
