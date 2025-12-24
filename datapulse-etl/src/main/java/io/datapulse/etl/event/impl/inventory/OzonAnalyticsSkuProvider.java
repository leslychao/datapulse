package io.datapulse.etl.event.impl.inventory;

import java.util.List;

public interface OzonAnalyticsSkuProvider {

  List<String> resolveSkus(long accountId);
}
