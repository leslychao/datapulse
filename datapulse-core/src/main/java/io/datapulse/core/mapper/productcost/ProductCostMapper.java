package io.datapulse.core.mapper.productcost;

import io.datapulse.core.entity.productcost.ProductCostEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.dto.productcost.ProductCostDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = TimeMapper.class,
    config = BaseMapperConfig.class
)
public interface ProductCostMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  ProductCostEntity toEntity(ProductCostDto dto);

  ProductCostDto toDto(ProductCostEntity entity);
}
