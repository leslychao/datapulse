package io.datapulse.core.repository.orderpnl;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.request.orderpnl.OrderPnlQueryRequest;
import io.datapulse.domain.dto.response.orderpnl.OrderPnlResponse;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderPnlReadJdbcRepository implements OrderPnlReadRepository {

  private static final String SELECT_SQL = """
      select
          account_id,
          source_platform,
          order_id,
          currency,
          first_finance_date,
          last_finance_date,
          revenue_gross,
          marketplace_commission_amount,
          logistics_cost_amount,
          penalties_amount,
          refund_amount,
          net_payout,
          pnl_amount,
          items_sold_count,
          returned_items_count,
          is_returned,
          has_penalties,
          updated_at
      from datapulse.mart_order_pnl
      where account_id = :accountId
        and (cast(:sourcePlatform as text) is null or source_platform = cast(:sourcePlatform as text))
        and (cast(:dateFrom as date) is null or last_finance_date >= cast(:dateFrom as date))
        and (cast(:dateTo as date) is null or last_finance_date <= cast(:dateTo as date))
        and (cast(:isReturned as boolean) is null or is_returned = cast(:isReturned as boolean))
        and (cast(:hasPenalties as boolean) is null or has_penalties = cast(:hasPenalties as boolean))
      order by last_finance_date desc
      limit :limit offset :offset
      """;

  private static final String COUNT_SQL = """
      select count(*)
      from datapulse.mart_order_pnl
      where account_id = :accountId
        and (cast(:sourcePlatform as text) is null or source_platform = cast(:sourcePlatform as text))
        and (cast(:dateFrom as date) is null or last_finance_date >= cast(:dateFrom as date))
        and (cast(:dateTo as date) is null or last_finance_date <= cast(:dateTo as date))
        and (cast(:isReturned as boolean) is null or is_returned = cast(:isReturned as boolean))
        and (cast(:hasPenalties as boolean) is null or has_penalties = cast(:hasPenalties as boolean))
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public Page<OrderPnlResponse> find(OrderPnlQueryRequest request, Pageable pageable) {

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", request.accountId())
        .addValue("sourcePlatform",
            request.sourcePlatform() == null ? null : request.sourcePlatform().name())
        .addValue("dateFrom", request.dateFrom())
        .addValue("dateTo", request.dateTo())
        .addValue("isReturned", request.isReturned())
        .addValue("hasPenalties", request.hasPenalties())
        .addValue("limit", pageable.getPageSize())
        .addValue("offset", pageable.getOffset());

    List<OrderPnlResponse> content = jdbcTemplate.query(
        SELECT_SQL,
        params,
        (rs, rowNum) -> new OrderPnlResponse(
            rs.getLong("account_id"),
            MarketplaceType.from(rs.getString("source_platform")),
            rs.getString("order_id"),
            rs.getString("currency"),
            rs.getDate("first_finance_date").toLocalDate(),
            rs.getDate("last_finance_date").toLocalDate(),
            rs.getBigDecimal("revenue_gross"),
            rs.getBigDecimal("marketplace_commission_amount"),
            rs.getBigDecimal("logistics_cost_amount"),
            rs.getBigDecimal("penalties_amount"),
            rs.getBigDecimal("refund_amount"),
            rs.getBigDecimal("net_payout"),
            rs.getBigDecimal("pnl_amount"),
            rs.getInt("items_sold_count"),
            rs.getInt("returned_items_count"),
            rs.getBoolean("is_returned"),
            rs.getBoolean("has_penalties"),
            rs.getObject("updated_at", OffsetDateTime.class)
        )
    );

    Long total = jdbcTemplate.queryForObject(COUNT_SQL, params, Long.class);
    long safeTotal = total == null ? 0L : total;

    return new PageImpl<>(content, pageable, safeTotal);
  }
}
