package io.datapulse.core.mapper;

import io.datapulse.core.entity.SalesFactEntity;
import io.datapulse.domain.dto.SalesFactDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface SalesFactMapper {

  SalesFactEntity toEntity(SalesFactDto dto);

  SalesFactDto toDto(SalesFactEntity entity);
}
