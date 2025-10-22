package ru.vkim.datapulse.dwh.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
class FactSaleUpsertOperationsImpl implements FactSaleUpsertOperations {

    private final DatabaseClient db;

    @Override
    public Mono<Integer> upsert(String marketplace, String shopId, String sku,
                                OffsetDateTime eventTime, Integer quantity, BigDecimal revenue) {
        String sql = """
            insert into fact.sales(marketplace, shop_id, sku, event_time, quantity, revenue)
            values ($1,$2,$3,$4,$5,$6)
            on conflict (marketplace, shop_id, sku, event_time)
            do update set quantity = excluded.quantity, revenue = excluded.revenue
            """;
        return db.sql(sql)
                .bind("$1", marketplace)
                .bind("$2", shopId)
                .bind("$3", sku)
                .bind("$4", eventTime)
                .bind("$5", quantity)
                .bind("$6", revenue)
                .fetch()
                .rowsUpdated();
    }
}
