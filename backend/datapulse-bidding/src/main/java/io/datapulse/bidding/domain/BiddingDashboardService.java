package io.datapulse.bidding.domain;

import java.util.List;
import java.util.Map;

import io.datapulse.bidding.api.BiddingDashboardResponse;
import io.datapulse.bidding.persistence.BiddingDashboardReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BiddingDashboardService {

  private final BiddingDashboardReadRepository dashboardRepo;

  @Transactional(readOnly = true)
  public BiddingDashboardResponse getDashboard(long workspaceId) {
    int totalManaged = dashboardRepo.countManagedProducts(workspaceId);
    int activePolicies = dashboardRepo.countActivePolicies(workspaceId);
    Map<String, Integer> productsByStrategy =
        dashboardRepo.countProductsByStrategy(workspaceId);
    Map<String, Integer> decisionsByType =
        dashboardRepo.countDecisionsByType(workspaceId);
    var runStats = dashboardRepo.runStatsLast7Days(workspaceId);
    List<BiddingDashboardResponse.TopProductItem> topHighDrr =
        dashboardRepo.topHighDrrProducts(workspaceId, 5);
    List<BiddingDashboardResponse.TopProductItem> topImproved =
        dashboardRepo.topImprovedProducts(workspaceId, 5);

    return new BiddingDashboardResponse(
        totalManaged,
        activePolicies,
        productsByStrategy,
        decisionsByType,
        runStats.total(),
        runStats.failed(),
        runStats.paused(),
        topHighDrr,
        topImproved);
  }
}
