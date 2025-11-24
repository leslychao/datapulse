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
                  coalesce(sum((p.doc ->> 'finishedPrice')::numeric), 0::numeric) as revenue,
                  0::numeric                            as search_views,
                  0::numeric                            as card_views,
                  0::numeric                            as cart_adds,
                  0::numeric                            as sessions,
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
                  (p.doc ->> 'nmId')                     as mp_item_id,
                  (p.doc ->> 'supplierArticle')          as mp_offer_id,
                  (p.doc ->> 'barcode')                  as barcode,
                  null::varchar                          as title,
                  (p.doc ->> 'brand')                    as brand,
                  (p.doc ->> 'category')                 as category,
                  null::varchar                          as mp_category,
                  (p.doc ->> 'subject')                  as subject,
                  false                                  as is_kgt,
                  false                                  as is_archived,
                  (p.doc ->> 'totalPrice')::numeric      as price_regular,
                  (p.doc ->> 'finishedPrice')::numeric   as price_sale,
                  null::numeric                          as vat_rate,
                  (p.doc ->> 'lastChangeDate')::timestamptz as last_synced_at
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
                  mp_item_id,
                  mp_offer_id,
                  barcode,
                  title,
                  brand,
                  category,
                  mp_category,
                  subject,
                  is_kgt,
                  is_archived,
                  price_regular,
                  price_sale,
                  vat_rate,
                  last_synced_at
              )
              select distinct
                  p.account_id,
                  p.marketplace,
                  p.mp_item_id,
                  p.mp_offer_id,
                  p.barcode,
                  p.title,
                  p.brand,
                  p.category,
                  p.mp_category,
                  p.subject,
                  p.is_kgt,
                  p.is_archived,
                  p.price_regular,
                  p.price_sale,
                  p.vat_rate,
                  p.last_synced_at
              from product_src p
              on conflict (account_id, marketplace, mp_item_id)
                  do update set
                      mp_offer_id    = excluded.mp_offer_id,
                      barcode        = excluded.barcode,
                      title          = excluded.title,
                      brand          = excluded.brand,
                      category       = excluded.category,
                      mp_category    = excluded.mp_category,
                      subject        = excluded.subject,
                      is_kgt         = excluded.is_kgt,
                      is_archived    = excluded.is_archived,
                      price_regular  = excluded.price_regular,
                      price_sale     = excluded.price_sale,
                      vat_rate       = excluded.vat_rate,
                      last_synced_at = excluded.last_synced_at
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
              payout_amount,
              commission_amount,
              delivery_amount,
              storage_amount,
              penalty_amount,
              ads_amount,
              other_fees_amount,
              currency_code,
              search_views,
              card_views,
              cart_adds,
              sessions,
              wb_open_count,
              wb_cart_count,
              wb_order_count,
              wb_buyout_count,
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
              s.revenue                                     as payout_amount,
              0                                             as commission_amount,
              0                                             as delivery_amount,
              0                                             as storage_amount,
              0                                             as penalty_amount,
              0                                             as ads_amount,
              0                                             as other_fees_amount,
              s.currency_code,
              s.search_views::integer,
              s.card_views::integer,
              s.cart_adds::integer,
              s.sessions::integer,
              0                                             as wb_open_count,
              0                                             as wb_cart_count,
              0                                             as wb_order_count,
              0                                             as wb_buyout_count,
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
                  payout_amount      = excluded.payout_amount,
                  commission_amount  = excluded.commission_amount,
                  delivery_amount    = excluded.delivery_amount,
                  storage_amount     = excluded.storage_amount,
                  penalty_amount     = excluded.penalty_amount,
                  ads_amount         = excluded.ads_amount,
                  other_fees_amount  = excluded.other_fees_amount,
                  currency_code      = excluded.currency_code,
                  search_views       = excluded.search_views,
                  card_views         = excluded.card_views,
                  cart_adds          = excluded.cart_adds,
                  sessions           = excluded.sessions,
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
