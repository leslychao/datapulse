package io.datapulse.etl.repository.ozon;

import io.datapulse.core.entity.SalesFactEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OzonSalesFactRepository extends JpaRepository<SalesFactEntity, Long> {

  @Modifying
  @Query(
      value = """
          with sales_src as (
              select distinct on (
                  rs.account_id,
                  rs.marketplace,
                  (p.doc ->> 'day')::date,
                  p.doc ->> 'offerId',
                  p.doc ->> 'warehouseId'
              )
                  rs.account_id,
                  rs.marketplace,
                  (p.doc ->> 'day')::date                        as sale_date,
                  p.doc ->> 'offerId'                            as mp_offer_id,
                  p.doc ->> 'warehouseId'                        as mp_warehouse_id,

                  coalesce((
                      select nullif(m ->> 'value', '')::numeric
                      from jsonb_array_elements(p.doc -> 'metrics') as m
                      where m ->> 'id' = 'revenue'
                      limit 1
                  ), 0::numeric)                                  as revenue,

                  coalesce((
                      select nullif(m ->> 'value', '')::numeric
                      from jsonb_array_elements(p.doc -> 'metrics') as m
                      where m ->> 'id' = 'ordered_units'
                      limit 1
                  ), 0::numeric)                                  as ordered_units,

                  coalesce((
                      select nullif(m ->> 'value', '')::numeric
                      from jsonb_array_elements(p.doc -> 'metrics') as m
                      where m ->> 'id' = 'returns'
                      limit 1
                  ), 0::numeric)                                  as returned_units,

                  coalesce((
                      select nullif(m ->> 'value', '')::numeric
                      from jsonb_array_elements(p.doc -> 'metrics') as m
                      where m ->> 'id' = 'cancellations'
                      limit 1
                  ), 0::numeric)                                  as canceled_units,

                  'RUB'::char(3)                                  as currency_code
              from raw_sales_fact_ozon rs
              cross join lateral (
                  select rs.payload::jsonb as doc
              ) as p
              where rs.account_id = :accountId
                and rs.marketplace = 'OZON'
                and rs.request_id = :requestId
                and (p.doc ->> 'day')::date between :dateFrom and :dateTo
              order by
                  rs.account_id,
                  rs.marketplace,
                  (p.doc ->> 'day')::date,
                  p.doc ->> 'offerId',
                  p.doc ->> 'warehouseId'
          ),

          product_src as (
              select distinct on (
                  rp.account_id,
                  rp.marketplace,
                  (q.doc ->> 'offer_id')
              )
                  rp.account_id,
                  rp.marketplace,
                  (q.doc ->> 'sku')                               as mp_item_id,
                  q.doc ->> 'offer_id'                            as mp_offer_id,
                  (q.doc -> 'barcodes' ->> 0)                     as mp_barcode,
                  q.doc ->> 'name'                                as title,
                  null::varchar                                   as brand,
                  null::varchar                                   as mp_category,
                  null::varchar                                   as mp_subject,
                  coalesce((q.doc ->> 'is_kgt')::boolean, false)      as mp_is_kgt,
                  coalesce((q.doc ->> 'is_archived')::boolean, false) as mp_is_archived,
                  nullif(q.doc ->> 'old_price', '')::numeric      as mp_price_regular,
                  nullif(q.doc ->> 'price', '')::numeric          as mp_price_sale,
                  nullif(q.doc ->> 'vat', '')::numeric            as mp_vat_rate,
                  (q.doc ->> 'updated_at')::timestamptz           as last_synced_at
              from raw_product_info_ozon rp
              cross join lateral (
                  select rp.payload::jsonb as doc
              ) as q
              where rp.account_id = :accountId
                and rp.marketplace = 'OZON'
                and rp.request_id = :requestId
              order by
                  rp.account_id,
                  rp.marketplace,
                  q.doc ->> 'offer_id'
          ),

          upsert_warehouse as (
              insert into warehouse (
                  marketplace,
                  mp_warehouse_id,
                  name,
                  region,
                  type,
                  last_synced_at
              )
              select distinct
                  s.marketplace,
                  s.mp_warehouse_id,
                  concat('OZON warehouse ', s.mp_warehouse_id)      as name,
                  null::varchar                                     as region,
                  null::varchar                                     as type,
                  now()                                             as last_synced_at
              from sales_src s
              where s.mp_warehouse_id is not null
              on conflict (marketplace, mp_warehouse_id, name)
                  do update set last_synced_at = excluded.last_synced_at
          ),

          upsert_catalog_item as (
              insert into catalog_item (
                  account_id,
                  marketplace,
                  title,
                  brand,
                  mp_item_id,
                  mp_offer_id,
                  mp_barcode,
                  mp_category,
                  mp_subject,
                  mp_is_kgt,
                  mp_is_archived,
                  mp_price_regular,
                  mp_price_sale,
                  mp_vat_rate,
                  last_synced_at
              )
              select distinct
                  p.account_id,
                  p.marketplace,
                  p.title,
                  p.brand,
                  p.mp_item_id,
                  p.mp_offer_id,
                  p.mp_barcode,
                  p.mp_category,
                  p.mp_subject,
                  p.mp_is_kgt,
                  p.mp_is_archived,
                  p.mp_price_regular,
                  p.mp_price_sale,
                  p.mp_vat_rate,
                  p.last_synced_at
              from product_src p
              on conflict (account_id, marketplace, mp_item_id)
                  do update set
                      title            = excluded.title,
                      brand            = excluded.brand,
                      mp_offer_id      = excluded.mp_offer_id,
                      mp_barcode       = excluded.mp_barcode,
                      mp_category      = excluded.mp_category,
                      mp_subject       = excluded.mp_subject,
                      mp_is_kgt        = excluded.mp_is_kgt,
                      mp_is_archived   = excluded.mp_is_archived,
                      mp_price_regular = excluded.mp_price_regular,
                      mp_price_sale    = excluded.mp_price_sale,
                      mp_vat_rate      = excluded.mp_vat_rate,
                      last_synced_at   = excluded.last_synced_at
          )

          insert into sales_fact (
              account_id,
              marketplace,
              sale_date,
              catalog_item_id,
              warehouse_id,
              ordered_units,
              delivered_units,
              returned_units,
              canceled_units,
              gmv_amount,
              currency_code,
              created_at,
              updated_at
          )
          select
              s.account_id,
              s.marketplace,
              s.sale_date,
              ci.id                                   as catalog_item_id,
              w.id                                    as warehouse_id,
              s.ordered_units::integer,
              greatest(
                  s.ordered_units - s.canceled_units - s.returned_units,
                  0
              )::integer                              as delivered_units,
              s.returned_units::integer,
              s.canceled_units::integer,
              s.revenue                               as gmv_amount,
              s.currency_code,
              now(),
              now()
          from sales_src s
          join product_src p
            on p.account_id  = s.account_id
           and p.marketplace = s.marketplace
           and p.mp_offer_id = s.mp_offer_id
          join catalog_item ci
            on ci.account_id  = p.account_id
           and ci.marketplace = p.marketplace
           and ci.mp_item_id  = p.mp_item_id
          left join warehouse w
            on w.marketplace     = s.marketplace
           and w.mp_warehouse_id = s.mp_warehouse_id
          on conflict (account_id, marketplace, sale_date, catalog_item_id, warehouse_id)
              do update set
                  ordered_units      = excluded.ordered_units,
                  delivered_units    = excluded.delivered_units,
                  returned_units     = excluded.returned_units,
                  canceled_units     = excluded.canceled_units,
                  gmv_amount         = excluded.gmv_amount,
                  currency_code      = excluded.currency_code,
                  updated_at         = now()
          """,
      nativeQuery = true
  )
  void materializeSalesFact(
      @Param("accountId") long accountId,
      @Param("dateFrom") LocalDate dateFrom,
      @Param("dateTo") LocalDate dateTo,
      @Param("requestId") String requestId
  );
}
