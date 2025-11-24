package io.datapulse.etl.repository.wb;

import io.datapulse.core.entity.SalesFactEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WbSalesFactRepository extends JpaRepository<SalesFactEntity, Long> {

  @Modifying
  @Query(
      value = """
          with sales_src as (
              select
                  r.account_id,
                  r.marketplace,
                  (p.doc ->> 'date')::date              as sale_date,
                  (p.doc ->> 'nmId')                    as mp_item_id,
                  (p.doc ->> 'supplierArticle')         as mp_offer_id,
                  (p.doc ->> 'warehouseName')           as mp_warehouse_id,
                  count(*)::numeric                     as ordered_units,
                  count(*)::numeric                     as delivered_units,
                  0::numeric                            as returned_units,
                  0::numeric                            as canceled_units,
                  coalesce(
                      sum(nullif(p.doc ->> 'finishedPrice', '')::numeric),
                      0::numeric
                  )                                     as revenue,
                  'RUB'::char(3)                        as currency_code
              from raw_realization_fact_wb r
              cross join lateral (
                  select r.payload::jsonb as doc
              ) as p
              where r.account_id = :accountId
                and r.marketplace = 'WILDBERRIES'
                and r.request_id = :requestId
                and (p.doc ->> 'date')::date between :dateFrom and :dateTo
              group by
                  r.account_id,
                  r.marketplace,
                  sale_date,
                  mp_item_id,
                  mp_offer_id,
                  mp_warehouse_id
          ),

          product_src as (
              select distinct on (
                  r.account_id,
                  r.marketplace,
                  (p.doc ->> 'nmId')
              )
                  r.account_id,
                  r.marketplace,
                  (p.doc ->> 'nmId')                         as mp_item_id,
                  (p.doc ->> 'supplierArticle')              as mp_offer_id,
                  (p.doc ->> 'barcode')                      as mp_barcode,
                  null::varchar                              as title,
                  (p.doc ->> 'brand')                        as brand,
                  (p.doc ->> 'category')                     as mp_category,
                  (p.doc ->> 'subject')                      as mp_subject,
                  false                                      as mp_is_kgt,
                  false                                      as mp_is_archived,
                  nullif(p.doc ->> 'totalPrice', '')::numeric    as mp_price_regular,
                  nullif(p.doc ->> 'finishedPrice', '')::numeric as mp_price_sale,
                  null::numeric                              as mp_vat_rate,
                  (p.doc ->> 'lastChangeDate')::timestamptz  as last_synced_at
              from raw_realization_fact_wb r
              cross join lateral (
                  select r.payload::jsonb as doc
              ) as p
              where r.account_id = :accountId
                and r.marketplace = 'WILDBERRIES'
                and r.request_id = :requestId
              order by
                  r.account_id,
                  r.marketplace,
                  (p.doc ->> 'nmId'),
                  (p.doc ->> 'lastChangeDate') desc
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
                  s.mp_warehouse_id                        as name,
                  null::varchar                            as region,
                  null::varchar                            as type,
                  now()                                    as last_synced_at
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
              ci.id                                         as catalog_item_id,
              w.id                                          as warehouse_id,
              s.ordered_units::integer,
              s.delivered_units::integer,
              s.returned_units::integer,
              s.canceled_units::integer,
              s.revenue                                     as gmv_amount,
              s.currency_code,
              now(),
              now()
          from sales_src s
          join product_src p
            on p.account_id  = s.account_id
           and p.marketplace = s.marketplace
           and p.mp_item_id  = s.mp_item_id
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
