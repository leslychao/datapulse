package io.datapulse.etl.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(ClickHouseMigrationProperties.class)
public class ClickHouseConfig {

    @Bean
    @ConfigurationProperties(prefix = "datapulse.clickhouse")
    public DataSourceProperties clickhouseDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource clickhouseDataSource(DataSourceProperties clickhouseDataSourceProperties) {
        return clickhouseDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate clickhouseJdbcTemplate(DataSource clickhouseDataSource) {
        return new JdbcTemplate(clickhouseDataSource);
    }
}
