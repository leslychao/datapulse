package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.promotions.persistence.PromoEvaluationRunEntity;
import org.mapstruct.Mapper;

@Mapper(config = BaseMapperConfig.class)
public interface PromoEvaluationRunMapper {

    PromoEvaluationRunResponse toResponse(PromoEvaluationRunEntity entity);
}
