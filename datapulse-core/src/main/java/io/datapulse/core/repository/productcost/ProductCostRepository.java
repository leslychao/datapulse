package io.datapulse.core.repository.productcost;

import io.datapulse.domain.dto.productcost.ProductCostDto;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductCostRepository {

  private static final Calendar UTC_CALENDAR = utcCalendar();

  private static final String UPSERT_SCD2_SQL = """
      with source as (
          select
              ?::bigint      as account_id,
              ?::bigint      as product_id,
              ?::numeric     as cost_value,
              ?::text        as currency,
              ?::timestamptz as valid_from_ts
      ),
      closed as (
          update fact_product_cost d
          set valid_to   = s.valid_from_ts - interval '1 microsecond',
              updated_at = now()
          from source s
          where d.account_id = s.account_id
            and d.product_id = s.product_id
            and d.valid_to is null
            and d.valid_from < s.valid_from_ts
            and (d.cost_value, d.currency) is distinct from (s.cost_value, s.currency)
          returning 1
      )
      insert into fact_product_cost (
          account_id,
          product_id,
          cost_value,
          currency,
          valid_from,
          valid_to,
          created_at,
          updated_at
      )
      select
          s.account_id,
          s.product_id,
          s.cost_value,
          s.currency,
          s.valid_from_ts,
          null::timestamptz,
          now(),
          now()
      from source s
      where
        (
          not exists (
              select 1
              from fact_product_cost cur
              where cur.account_id = s.account_id
                and cur.product_id = s.product_id
                and cur.valid_to is null
          )
          or exists (select 1 from closed)
        )
        and not exists (
          select 1
          from fact_product_cost cur
          where cur.account_id = s.account_id
            and cur.product_id = s.product_id
            and cur.valid_to is null
            and (cur.cost_value, cur.currency) is not distinct from (s.cost_value, s.currency)
        )
      on conflict (account_id, product_id, valid_from) do nothing;
      """;

  private final JdbcTemplate jdbcTemplate;

  public void upsertBatchFromDtos(
      Long accountId,
      List<ProductCostDto> items,
      Instant validFromTs) {

    if (items.isEmpty()) {
      return;
    }

    jdbcTemplate.batchUpdate(
        UPSERT_SCD2_SQL,
        items,
        items.size(),
        (PreparedStatement ps, ProductCostDto item) -> bind(
            ps,
            accountId,
            item.getProductId(),
            item.getCostValue(),
            item.getCurrency(),
            validFromTs)
    );
  }

  private void bind(
      PreparedStatement ps,
      Long accountId,
      Long productId,
      BigDecimal costValue,
      String currency,
      Instant validFromTs
  ) throws SQLException {
    ps.setLong(1, accountId);
    ps.setLong(2, productId);
    ps.setBigDecimal(3, costValue);
    ps.setString(4, currency);
    ps.setTimestamp(5, Timestamp.from(validFromTs), UTC_CALENDAR);
  }

  private static Calendar utcCalendar() {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.setLenient(false);
    return calendar;
  }
}
