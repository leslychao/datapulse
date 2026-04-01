package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.promotions.persistence.PromoEvaluationEntity;
import org.mapstruct.Mapper;

@Mapper(config = BaseMapperConfig.class)
public interface PromoEvaluationMapper {

    PromoEvaluationResponse toResponse(PromoEvaluationEntity entity);
}
