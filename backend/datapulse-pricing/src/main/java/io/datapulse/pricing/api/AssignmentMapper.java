package io.datapulse.pricing.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.pricing.persistence.PricePolicyAssignmentEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface AssignmentMapper {

    AssignmentResponse toResponse(PricePolicyAssignmentEntity entity);

    List<AssignmentResponse> toResponses(List<PricePolicyAssignmentEntity> entities);
}
