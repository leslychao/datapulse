package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.platform.config.JsonReadHelper;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = JsonReadHelper.class)
public interface PricePolicyMapper {

    @Mapping(target = "strategyParams", qualifiedByName = "jsonToObject")
    @Mapping(target = "guardConfig", qualifiedByName = "jsonToObject")
    PricePolicyResponse toResponse(PricePolicyEntity entity);

    PricePolicySummaryResponse toSummary(PricePolicyEntity entity);

    List<PricePolicySummaryResponse> toSummaries(List<PricePolicyEntity> entities);
}
