package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.platform.config.JsonReadHelper;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = BaseMapperConfig.class, uses = JsonReadHelper.class)
public interface PromoDecisionMapper {

    @Mapping(target = "policySnapshot", qualifiedByName = "jsonToObject")
    PromoDecisionResponse toResponse(PromoDecisionEntity entity);
}
