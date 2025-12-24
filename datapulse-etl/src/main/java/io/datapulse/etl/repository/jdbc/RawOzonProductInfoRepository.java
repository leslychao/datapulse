package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RawOzonProductInfoRepository {

  private static final String SELECT_SKU_FROM_PAYLOAD = """
      select distinct
          payload ->> 'sku' as sku
      from raw_ozon_product_info
      where account_id = ?
        and marketplace = ?
        and coalesce(payload ->> 'sku', '') <> ''
      order by sku
      """;

  private final JdbcTemplate jdbcTemplate;

  public List<String> fetchAllSkus(long accountId) {
    return jdbcTemplate.queryForList(
        SELECT_SKU_FROM_PAYLOAD,
        String.class,
        accountId,
        MarketplaceType.OZON.name()
    );
  }
}
