package io.datapulse.etl.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@EnableConfigurationProperties(ClickHouseMigrationProperties.class)
public class ClickHouseConfig {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
        JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Bean("clickhouseJdbcTemplate")
    public JdbcTemplate clickhouseJdbcTemplate(
        @Value("${datapulse.clickhouse.url}") String url,
        @Value("${datapulse.clickhouse.username}") String username,
        @Value("${datapulse.clickhouse.password:}") String password) {
        var ds = DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
            .build();
        return new JdbcTemplate(ds);
    }
}
