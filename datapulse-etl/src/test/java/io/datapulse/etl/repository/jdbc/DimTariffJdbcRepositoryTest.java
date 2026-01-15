package io.datapulse.etl.repository.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringJUnitConfig(classes = DimTariffJdbcRepositoryTest.TestConfig.class)
@ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    JdbcTemplateAutoConfiguration.class
})
class DimTariffJdbcRepositoryTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private DimTariffJdbcRepository repository;

  @BeforeEach
  void setUpSchema() {
    jdbcTemplate.execute("""
        create table if not exists dim_tariff_ozon (
          id bigserial primary key,
          product_id bigint not null,
          offer_id text,
          acquiring numeric(10,4),

          sales_percent_fbo numeric(10,4),
          sales_percent_fbs numeric(10,4),
          sales_percent_rfbs numeric(10,4),
          sales_percent_fbp numeric(10,4),

          fbo_deliv_to_customer_amount numeric(10,4),
          fbo_direct_flow_trans_min_amount numeric(10,4),
          fbo_direct_flow_trans_max_amount numeric(10,4),
          fbo_return_flow_amount numeric(10,4),

          fbs_deliv_to_customer_amount numeric(10,4),
          fbs_direct_flow_trans_min_amount numeric(10,4),
          fbs_direct_flow_trans_max_amount numeric(10,4),
          fbs_first_mile_min_amount numeric(10,4),
          fbs_first_mile_max_amount numeric(10,4),
          fbs_return_flow_amount numeric(10,4),

          valid_from timestamptz not null,
          valid_to timestamptz,

          created_at timestamptz not null default now(),
          updated_at timestamptz not null default now()
        );
        """);

    jdbcTemplate.execute("""
        create unique index if not exists uq_dim_tariff_ozon_scd2
          on dim_tariff_ozon (product_id, valid_from);
        """);

    jdbcTemplate.execute("""
        create unique index if not exists uq_dim_tariff_ozon_current
          on dim_tariff_ozon (product_id)
          where valid_to is null;
        """);

    jdbcTemplate.execute("""
        create table if not exists raw_ozon_product_info_prices (
          account_id bigint not null,
          request_id text not null,
          payload jsonb not null,
          created_at timestamptz not null
        );
        """);
  }

  @Test
  void shouldBeIdempotentOnRetry() {
    long accountId = 1L;
    String requestId = "retry";
    OffsetDateTime t = OffsetDateTime.parse("2026-01-15T10:00:00Z");

    insertRaw(accountId, requestId, t, 19.1);

    repository.upsertOzon(accountId, requestId);
    repository.upsertOzon(accountId, requestId);

    Integer count = jdbcTemplate.queryForObject(
        "select count(*) from dim_tariff_ozon where product_id = 1074782997",
        Integer.class
    );

    assertThat(count).isEqualTo(1);
  }

  @Test
  void shouldClosePreviousAndInsertNewVersionWhenTariffChanged() {
    long accountId = 1L;
    String requestId1 = "r1";
    String requestId2 = "r2";

    OffsetDateTime t1 = OffsetDateTime.parse("2026-01-15T10:00:00Z");
    OffsetDateTime t2 = OffsetDateTime.parse("2026-01-16T10:00:00Z");

    insertRaw(accountId, requestId1, t1, 19.1);
    repository.upsertOzon(accountId, requestId1);

    insertRaw(accountId, requestId2, t2, 20.5);
    repository.upsertOzon(accountId, requestId2);

    Integer versions = jdbcTemplate.queryForObject(
        "select count(*) from dim_tariff_ozon where product_id = 1074782997",
        Integer.class
    );
    assertThat(versions).isEqualTo(2);

    OffsetDateTime closedValidTo = jdbcTemplate.queryForObject(
        """
            select valid_to
            from dim_tariff_ozon
            where product_id = 1074782997
              and valid_to is not null
            order by valid_from
            limit 1
            """,
        OffsetDateTime.class
    );
    assertThat(closedValidTo).isEqualTo(t2.minusNanos(1_000));

    Integer currentOpen = jdbcTemplate.queryForObject(
        """
            select count(*)
            from dim_tariff_ozon
            where product_id = 1074782997
              and valid_to is null
            """,
        Integer.class
    );
    assertThat(currentOpen).isEqualTo(1);
  }

  private void insertRaw(long accountId, String requestId, OffsetDateTime createdAt,
      double salesPercentFbo) {
    jdbcTemplate.update("""
            insert into raw_ozon_product_info_prices (
              account_id, request_id, payload, created_at
            )
            values (?, ?, jsonb_build_object(
              'product_id', 1074782997,
              'offer_id', '588889995_белый',
              'acquiring', 2.77,
              'commissions', jsonb_build_object(
                'sales_percent_fbo', ?,
                'sales_percent_fbs', 19.1,
                'sales_percent_rfbs', 44.1,
                'sales_percent_fbp', 44.1,
                'fbo_deliv_to_customer_amount', 25.0,
                'fbo_direct_flow_trans_min_amount', 19.32,
                'fbo_direct_flow_trans_max_amount', 45.86,
                'fbo_return_flow_amount', 19.32,
                'fbs_deliv_to_customer_amount', 25.0,
                'fbs_direct_flow_trans_min_amount', 19.32,
                'fbs_direct_flow_trans_max_amount', 19.32,
                'fbs_first_mile_min_amount', 10.0,
                'fbs_first_mile_max_amount', 30.0,
                'fbs_return_flow_amount', 19.32
              )
            ), ?)
            """,
        accountId,
        requestId,
        salesPercentFbo,
        createdAt
    );
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    DimTariffJdbcRepository dimTariffJdbcRepository(JdbcTemplate jdbcTemplate) {
      return new DimTariffJdbcRepository(jdbcTemplate);
    }
  }
}
