package ru.vkim.datapulse.dwh.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Value
@Builder
@Table("ref.shops")
public class ShopEntity {
    @Id
    Long id;

    @Column("marketplace")
    String marketplace; // "wb" | "ozon"

    @Column("shop_id")
    String shopId;      // WB: не всегда нужен, Ozon: Client-Id

    @Column("token")
    String token;       // WB: Authorization, Ozon: Api-Key

    @Column("enabled")
    Boolean enabled;
}
