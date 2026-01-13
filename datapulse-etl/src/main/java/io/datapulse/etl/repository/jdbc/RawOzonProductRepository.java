package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RawOzonProductRepository {

  private static final String SELECT_PRODUCT_IDS = """
      select distinct
          (payload::jsonb ->> 'product_id')::bigint as product_id
      from %s
      where account_id = ?
        and marketplace = ?
        and nullif(payload::jsonb ->> 'product_id', '') is not null
      order by product_id
      """;

  private final JdbcTemplate jdbcTemplate;

  public List<Long> fetchAllProductIds(long accountId) {
    String sql = SELECT_PRODUCT_IDS.formatted(RawTableNames.RAW_OZON_PRODUCTS);

    return jdbcTemplate.queryForList(
        sql,
        Long.class,
        accountId,
        MarketplaceType.OZON.name()
    );
  }
}
