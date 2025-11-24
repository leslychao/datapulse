package io.datapulse.marketplaces.adapter;

import io.datapulse.marketplaces.dto.Snapshot;
import java.time.LocalDate;

public interface MarketplaceAdapterOps {

  Snapshot<?> downloadSalesSnapshot(long accountId, LocalDate from, LocalDate to);
}
