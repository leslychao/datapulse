package ru.vkim.datapulse.dwh.repo;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface FactSaleUpsertOperations {
    Mono<Integer> upsert(String marketplace, String shopId, String sku,
                         OffsetDateTime eventTime, Integer quantity, BigDecimal revenue);
}
