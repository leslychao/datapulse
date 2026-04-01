package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface PricePolicyMapper {

    PricePolicyResponse toResponse(PricePolicyEntity entity);

    PricePolicySummaryResponse toSummary(PricePolicyEntity entity);

    List<PricePolicySummaryResponse> toSummaries(List<PricePolicyEntity> entities);
}
