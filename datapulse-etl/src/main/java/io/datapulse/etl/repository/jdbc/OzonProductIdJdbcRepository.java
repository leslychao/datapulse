package io.datapulse.etl.repository.jdbc;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OzonProductIdJdbcRepository {

  private static final String SELECT_PRODUCT_IDS = """
      select p.product_id
      from dim_product_ozon p
      where p.account_id = ?
      order by p.product_id
      """;

  private final JdbcTemplate jdbcTemplate;

  public List<Long> fetchAllProductIds(long accountId) {
    return jdbcTemplate.queryForList(SELECT_PRODUCT_IDS, Long.class, accountId);
  }
}
