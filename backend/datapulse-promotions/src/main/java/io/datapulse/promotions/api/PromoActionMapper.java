package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.promotions.persistence.PromoActionEntity;
import org.mapstruct.Mapper;

@Mapper(config = BaseMapperConfig.class)
public interface PromoActionMapper {

    PromoActionResponse toResponse(PromoActionEntity entity);
}
