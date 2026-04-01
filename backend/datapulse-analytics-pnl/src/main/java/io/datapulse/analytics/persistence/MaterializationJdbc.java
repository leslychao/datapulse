package io.datapulse.analytics.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MaterializationJdbc {

    private final NamedParameterJdbcTemplate pg;
    private final JdbcTemplate ch;

    public MaterializationJdbc(
            NamedParameterJdbcTemplate pgJdbcTemplate,
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
        this.pg = pgJdbcTemplate;
        this.ch = clickhouseJdbcTemplate;
    }

    public NamedParameterJdbcTemplate pg() {
        return pg;
    }

    public JdbcTemplate ch() {
        return ch;
    }
}
