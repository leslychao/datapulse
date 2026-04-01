package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.platform.config.JsonReadHelper;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = BaseMapperConfig.class, uses = JsonReadHelper.class)
public interface PriceDecisionMapper {

    @Mapping(target = "signalSnapshot", qualifiedByName = "jsonToObject")
    @Mapping(target = "constraintsApplied", qualifiedByName = "jsonToObject")
    @Mapping(target = "guardsEvaluated", qualifiedByName = "jsonToObject")
    PriceDecisionResponse toResponse(PriceDecisionEntity entity);
}
