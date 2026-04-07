package io.datapulse.etl.persistence.canonical;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "marketplace_offer")
public class MarketplaceOfferEntity extends BaseEntity {

    @Column(name = "seller_sku_id", nullable = false)
    private Long sellerSkuId;

    @Column(name = "marketplace_connection_id", nullable = false)
    private Long marketplaceConnectionId;

    @Column(name = "marketplace_type", nullable = false, length = 10)
    private String marketplaceType;

    @Column(name = "marketplace_sku", nullable = false, length = 120)
    private String marketplaceSku;

    @Column(name = "marketplace_sku_alt", length = 120)
    private String marketplaceSkuAlt;

    @Column(length = 500)
    private String name;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 1000)
    private String url;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
