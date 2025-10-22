package ru.vkim.datapulse.dwh.repo;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.vkim.datapulse.dwh.model.FactSaleEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface FactSaleRepository
        extends ReactiveCrudRepository<FactSaleEntity, Long>, FactSaleUpsertOperations {

    Flux<FactSaleEntity> findByMarketplaceAndShopIdAndEventTimeBetweenOrderByEventTimeDesc(
            String marketplace, String shopId, OffsetDateTime from, OffsetDateTime to
    );

    @Modifying
    @Query("""

        insert into fact.sales (marketplace, shop_id, sku, event_time, quantity, revenue)

        values (:marketplace, :shopId, :sku, :eventTime, :quantity, :revenue)

        on conflict (marketplace, shop_id, sku, event_time)

        do update set quantity = excluded.quantity, revenue = excluded.revenue

        """)
    Mono<Integer> upsert(
            @Param("marketplace") String marketplace,
            @Param("shopId") String shopId,
            @Param("sku") String sku,
            @Param("eventTime") OffsetDateTime eventTime,
            @Param("quantity") Integer quantity,
            @Param("revenue") BigDecimal revenue
    );
}
