package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.repository.ReturnsFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReturnsFactJdbcRepository implements ReturnsFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_returns (
          account_id,
          source_platform,
          source_event_id,
          dim_product_id,
          warehouse_id,
          category_id,
          quantity,
          return_date,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          source_event_id,
          dim_product_id,
          warehouse_id,
          category_id,
          quantity,
          return_date,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, source_event_id)
      do update
      set
          dim_product_id = excluded.dim_product_id,
          warehouse_id   = excluded.warehouse_id,
          category_id    = excluded.category_id,
          quantity       = excluded.quantity,
          return_date    = excluded.return_date,
          updated_at     = now();
      """;

  private static final String WB_SOURCE_SELECT = """
      select
          :accountId                 as account_id,
          :platform                  as source_platform,
          s.source_event_id          as source_event_id,
          s.dim_product_id           as dim_product_id,
          s.warehouse_id             as warehouse_id,
          s.category_id              as category_id,
          s.quantity                 as quantity,
          s.return_date              as return_date
      from (
          select
              (r.payload::jsonb ->> 'saleID') || ':' || (r.payload::jsonb ->> 'nmId') as source_event_id,
              dp.id                                                                   as dim_product_id,
              dw.id                                                                   as warehouse_id,
              dc.id                                                                   as category_id,
              count(*)                                                                as quantity,
              min((r.payload::jsonb ->> 'date')::timestamptz)::date                   as return_date
          from raw_wb_supplier_sales r
          join dim_product dp
            on dp.account_id        = r.account_id
           and dp.source_platform   = :platform
           and dp.source_product_id = (r.payload::jsonb ->> 'nmId')
          left join dim_warehouse dw
            on dw.account_id      = r.account_id
           and dw.source_platform = :platform
           and dw.warehouse_name  = (r.payload::jsonb ->> 'warehouseName')
          left join dim_category dc
            on dc.source_platform    = :platform
           and dc.source_category_id = dp.external_category_id
          where r.account_id = :accountId
            and r.request_id = :requestId
            and (r.payload::jsonb ->> 'saleID') ~ '^[RA]'
          group by
            (r.payload::jsonb ->> 'saleID'),
            (r.payload::jsonb ->> 'nmId'),
            dp.id,
            dw.id,
            dc.id
      ) as s
      """;

  private static final String OZON_SOURCE_SELECT = """
      select distinct
          :accountId                                                   as account_id,
          :platform                                                    as source_platform,
          (r.payload::jsonb ->> 'id')                                  as source_event_id,
          dp.id                                                        as dim_product_id,
          dw.id                                                        as warehouse_id,
          dc.id                                                        as category_id,
          (r.payload::jsonb #>> '{product,quantity}')::int             as quantity,
          (r.payload::jsonb #>> '{logistic,return_date}')::date        as return_date
      from raw_ozon_returns r
      join dim_product dp
        on dp.account_id        = r.account_id
       and dp.source_platform   = :platform
       and dp.source_product_id = (r.payload::jsonb #>> '{product,sku}')
      left join dim_warehouse dw
        on dw.account_id            = r.account_id
       and dw.source_platform       = :platform
       and dw.external_warehouse_id = (r.payload::jsonb #>> '{place,id}')
      left join dim_category dc
        on dc.source_platform    = :platform
       and dc.source_category_id = dp.external_category_id
      where r.account_id = :accountId
        and r.request_id = :requestId
      """;

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String platform = MarketplaceType.WILDBERRIES.tag();
    String query = UPSERT_TEMPLATE.formatted(WB_SOURCE_SELECT);
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("platform", platform);
    namedParameterJdbcTemplate.update(query, params);
  }

  @Override
  public void upsertOzonReturns(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String platform = MarketplaceType.OZON.tag();
    String query = UPSERT_TEMPLATE.formatted(OZON_SOURCE_SELECT);
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("platform", platform);
    namedParameterJdbcTemplate.update(query, params);
  }
}
