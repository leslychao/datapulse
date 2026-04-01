package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import org.mapstruct.Mapper;

@Mapper(config = BaseMapperConfig.class)
public interface PriceDecisionMapper {

    PriceDecisionResponse toResponse(PriceDecisionEntity entity);
}
