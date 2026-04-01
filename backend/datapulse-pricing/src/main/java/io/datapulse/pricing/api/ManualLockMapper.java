package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.pricing.persistence.ManualPriceLockEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface ManualLockMapper {

    ManualLockResponse toResponse(ManualPriceLockEntity entity);

    List<ManualLockResponse> toResponses(List<ManualPriceLockEntity> entities);
}
