package io.datapulse.etl.domain.normalized;

public record NormalizedCatalogItem(
        String sellerSku,
        String marketplaceSku,
        String marketplaceSkuAlt,
        String name,
        String brand,
        String category,
        String barcode,
        String status
) {}
