package io.datapulse.common.promo;

import java.util.List;

/**
 * Port for reacting to ETL-detected stale promo campaigns. Implemented in {@code datapulse-promotions}.
 */
public interface StalePromoCampaignHandler {

  void onCampaignsStale(List<Long> campaignIds);
}
