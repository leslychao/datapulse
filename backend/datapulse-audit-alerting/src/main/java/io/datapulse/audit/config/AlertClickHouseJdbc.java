package io.datapulse.audit.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertClickHouseJdbc {

    private final NamedParameterJdbcTemplate ch;

    public AlertClickHouseJdbc(
        @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
        this.ch = new NamedParameterJdbcTemplate(clickhouseJdbcTemplate);
    }

    public NamedParameterJdbcTemplate ch() {
        return ch;
    }
}
