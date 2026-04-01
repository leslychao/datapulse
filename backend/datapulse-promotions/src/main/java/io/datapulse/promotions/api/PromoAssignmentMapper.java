package io.datapulse.promotions.api;

import io.datapulse.platform.config.BaseMapperConfig;
import io.datapulse.promotions.persistence.PromoPolicyAssignmentEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface PromoAssignmentMapper {

    PromoAssignmentResponse toResponse(PromoPolicyAssignmentEntity entity);

    List<PromoAssignmentResponse> toResponses(List<PromoPolicyAssignmentEntity> entities);
}
