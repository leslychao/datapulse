package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.pricing.persistence.ManualPriceLockEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface ManualLockMapper {

    @Mapping(target = "offerName", ignore = true)
    @Mapping(target = "sellerSku", ignore = true)
    @Mapping(target = "sourcePlatform", ignore = true)
    @Mapping(target = "connectionName", ignore = true)
    @Mapping(target = "lockedByName", ignore = true)
    ManualLockResponse toResponse(ManualPriceLockEntity entity);

    List<ManualLockResponse> toResponses(List<ManualPriceLockEntity> entities);
}
