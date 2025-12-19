package io.datapulse.etl.repository.jdbc;

import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.OzonProductCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OzonProductCommissionJdbcRepository implements OzonProductCommissionRepository {

  private static final String INSERT_TEMPLATE = """
      insert into fact_product_commission_ozon (
          account_id,
          product_id,
          offer_id,
          fulfillment_schema,
          snapshot_date,
          sales_percent,
          deliv_to_customer_amount,
          direct_flow_trans_min_amount,
          direct_flow_trans_max_amount,
          first_mile_min_amount,
          first_mile_max_amount,
          return_flow_amount,
          volume_weight,
          created_at
      )
      select
          account_id,
          product_id,
          offer_id,
          fulfillment_schema,
          snapshot_date,
          sales_percent,
          deliv_to_customer_amount,
          direct_flow_trans_min_amount,
          direct_flow_trans_max_amount,
          first_mile_min_amount,
          first_mile_max_amount,
          return_flow_amount,
          volume_weight,
          now()
      from (%s) as source
      on conflict (account_id, product_id, offer_id, fulfillment_schema, snapshot_date) do update
        set sales_percent                  = excluded.sales_percent,
            deliv_to_customer_amount       = excluded.deliv_to_customer_amount,
            direct_flow_trans_min_amount   = excluded.direct_flow_trans_min_amount,
            direct_flow_trans_max_amount   = excluded.direct_flow_trans_max_amount,
            first_mile_min_amount          = excluded.first_mile_min_amount,
            first_mile_max_amount          = excluded.first_mile_max_amount,
            return_flow_amount             = excluded.return_flow_amount,
            volume_weight                  = excluded.volume_weight;
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    String selectQuery = """
        with base as (
            select
                t.account_id                                         as account_id,
                (t.payload::jsonb ->> 'product_id')::bigint          as product_id,
                (t.payload::jsonb ->> 'offer_id')::text              as offer_id,
                (t.payload::jsonb ->> 'volume_weight')::numeric      as volume_weight,
                t.payload::jsonb -> 'commissions'                    as commissions
            from %1$s t
            where t.account_id = ? and t.request_id = ?
        ),
        expanded as (
            select
                b.account_id,
                b.product_id,
                b.offer_id,
                b.volume_weight,
                s.schema as fulfillment_schema,
                current_date as snapshot_date,
                case s.schema
                  when 'FBO'  then (b.commissions ->> 'sales_percent_fbo')::numeric
                  when 'FBS'  then (b.commissions ->> 'sales_percent_fbs')::numeric
                  when 'RFBS' then (b.commissions ->> 'sales_percent_rfbs')::numeric
                  when 'FBP'  then (b.commissions ->> 'sales_percent_fbp')::numeric
                end as sales_percent,

                case s.schema
                  when 'FBO' then (b.commissions ->> 'fbo_deliv_to_customer_amount')::numeric
                  when 'FBS' then (b.commissions ->> 'fbs_deliv_to_customer_amount')::numeric
                end as deliv_to_customer_amount,

                case s.schema
                  when 'FBO' then (b.commissions ->> 'fbo_direct_flow_trans_min_amount')::numeric
                  when 'FBS' then (b.commissions ->> 'fbs_direct_flow_trans_min_amount')::numeric
                end as direct_flow_trans_min_amount,

                case s.schema
                  when 'FBO' then (b.commissions ->> 'fbo_direct_flow_trans_max_amount')::numeric
                  when 'FBS' then (b.commissions ->> 'fbs_direct_flow_trans_max_amount')::numeric
                end as direct_flow_trans_max_amount,

                case s.schema
                  when 'FBS' then (b.commissions ->> 'fbs_first_mile_min_amount')::numeric
                end as first_mile_min_amount,

                case s.schema
                  when 'FBS' then (b.commissions ->> 'fbs_first_mile_max_amount')::numeric
                end as first_mile_max_amount,

                case s.schema
                  when 'FBO' then (b.commissions ->> 'fbo_return_flow_amount')::numeric
                  when 'FBS' then (b.commissions ->> 'fbs_return_flow_amount')::numeric
                end as return_flow_amount
            from base b
            cross join (select unnest(array['FBO','FBS','RFBS','FBP']) as schema) s
        )
        select
            e.account_id,
            e.product_id,
            e.offer_id,
            e.fulfillment_schema,
            e.snapshot_date,
            e.sales_percent,
            e.deliv_to_customer_amount,
            e.direct_flow_trans_min_amount,
            e.direct_flow_trans_max_amount,
            e.first_mile_min_amount,
            e.first_mile_max_amount,
            e.return_flow_amount,
            e.volume_weight
        from expanded e
        where e.sales_percent is not null
        """.formatted(RawTableNames.RAW_OZON_PRODUCT_INFO_PRICES);

    String sql = INSERT_TEMPLATE.formatted(selectQuery);
    jdbcTemplate.update(sql, accountId, requestId);
  }
}
