package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import java.time.LocalDate;
import reactor.core.publisher.Flux;

public interface MarketplaceAdapter<S, ST, F, R> {

  MarketplaceType type();

  Flux<S>  fetchSales(long accountId, LocalDate from, LocalDate to);

  Flux<ST> fetchStock(long accountId, LocalDate onDate);

  Flux<F>  fetchFinance(long accountId, LocalDate from, LocalDate to);

  Flux<R>  fetchReviews(long accountId, LocalDate from, LocalDate to);
}
