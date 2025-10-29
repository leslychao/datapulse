package io.datapulse.marketplaces.event;

import io.datapulse.domain.MarketplaceType;
import java.time.LocalDate;
import reactor.core.publisher.Flux;

/** Универсальный контракт «фетчера» под бизнес-событие. */
public interface EventFetcher<T> {
  /** Какой бизнес-эвент закрывает фетчер. */
  BusinessEvent event();

  /** Для какого маркетплейса. */
  MarketplaceType marketplace();

  /** Тип результата после маппинга во внутреннюю DTO. */
  Class<T> resultType();

  /** Запрос на выборку. */
  Flux<T> fetch(FetchRequest req);

  /** Универсальная модель запроса. */
  record FetchRequest(long accountId, LocalDate from, LocalDate to) {}
}
