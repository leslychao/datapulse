package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.pricing.persistence.PricingRunEntity;
import org.mapstruct.Mapper;

@Mapper(config = BaseMapperConfig.class)
public interface PricingRunMapper {

    PricingRunResponse toResponse(PricingRunEntity entity);
}
