package io.datapulse.etl.domain.normalized;

public record NormalizedCategory(
        String externalCategoryId,
        String name,
        String parentExternalCategoryId,
        String marketplaceType
) {}
