package io.datapulse.etl.event.impl.inventory;

import java.util.List;

public interface OzonAnalyticsSkuProvider {

  List<Long> resolveSkus(long accountId);
}
