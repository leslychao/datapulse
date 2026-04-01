package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.promotions.persistence.PromoPolicyEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface PromoPolicyMapper {

    PromoPolicyResponse toResponse(PromoPolicyEntity entity);

    PromoPolicySummaryResponse toSummary(PromoPolicyEntity entity);

    List<PromoPolicySummaryResponse> toSummaries(List<PromoPolicyEntity> entities);
}
