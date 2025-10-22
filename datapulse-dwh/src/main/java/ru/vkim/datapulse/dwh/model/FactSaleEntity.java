package ru.vkim.datapulse.dwh.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Value
@Builder
@Table("fact.sales")
public class FactSaleEntity {
    @Id
    Long id;

    @Column("marketplace")
    String marketplace;

    @Column("shop_id")
    String shopId;

    @Column("sku")
    String sku;

    @Column("event_time")
    OffsetDateTime eventTime;

    @Column("quantity")
    Integer quantity;

    @Column("revenue")
    BigDecimal revenue;
}
