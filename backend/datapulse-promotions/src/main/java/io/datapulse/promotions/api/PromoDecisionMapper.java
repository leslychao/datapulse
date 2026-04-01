package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import org.mapstruct.Mapper;

@Mapper(config = BaseMapperConfig.class)
public interface PromoDecisionMapper {

    PromoDecisionResponse toResponse(PromoDecisionEntity entity);
}
