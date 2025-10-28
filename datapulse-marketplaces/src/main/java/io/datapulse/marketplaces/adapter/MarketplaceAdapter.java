package io.datapulse.marketplaces.adapter;

import java.time.LocalDate;
import reactor.core.publisher.Flux;

public interface MarketplaceAdapter<S, ST, F, R> {

  Flux<S> fetchSales(long accountId, LocalDate from, LocalDate to);

  Flux<ST> fetchStock(long accountId, LocalDate onDate);

  Flux<F> fetchFinance(long accountId, LocalDate from, LocalDate to);

  Flux<R> fetchReviews(long accountId, LocalDate from, LocalDate to);
}
