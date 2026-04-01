package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.platform.config.JsonReadHelper;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = JsonReadHelper.class)
public interface PromoPolicyMapper {

    @Mapping(target = "autoParticipateCategories", qualifiedByName = "jsonToStringList")
    @Mapping(target = "autoDeclineCategories", qualifiedByName = "jsonToStringList")
    @Mapping(target = "evaluationConfig", qualifiedByName = "jsonToObject")
    PromoPolicyResponse toResponse(PromoPolicyEntity entity);

    PromoPolicySummaryResponse toSummary(PromoPolicyEntity entity);

    List<PromoPolicySummaryResponse> toSummaries(List<PromoPolicyEntity> entities);
}
