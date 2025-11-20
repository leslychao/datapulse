package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.marketplace.Snapshot;
import java.time.LocalDate;

public interface MarketplaceAdapterOps {

  Snapshot<?> downloadSalesSnapshot(long accountId, LocalDate from, LocalDate to);
}
