package io.datapulse.adapter.marketplaces;

import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.domain.dto.ReviewDto;
import io.datapulse.domain.dto.SaleDto;
import io.datapulse.domain.dto.StockDto;
import java.time.LocalDate;
import reactor.core.publisher.Flux;

public interface MarketplaceAdapter {

  Flux<SaleDto> fetchSales(long accountId, LocalDate from, LocalDate to);

  Flux<StockDto> fetchStock(long accountId, LocalDate onDate);

  Flux<FinanceDto> fetchFinance(long accountId, LocalDate from, LocalDate to);

  Flux<ReviewDto> fetchReviews(long accountId, LocalDate from, LocalDate to);
}
