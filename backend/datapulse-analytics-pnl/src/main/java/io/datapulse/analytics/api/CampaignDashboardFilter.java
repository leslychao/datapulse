package io.datapulse.analytics.api;

import java.util.List;

public record CampaignDashboardFilter(
    List<Long> connectionIds,
    String period,
    String status
) {

  private static final int DEFAULT_PERIOD_DAYS = 30;

  public int periodDays() {
    if ("7d".equals(period)) return 7;
    if ("30d".equals(period)) return 30;
    return DEFAULT_PERIOD_DAYS;
  }
}
