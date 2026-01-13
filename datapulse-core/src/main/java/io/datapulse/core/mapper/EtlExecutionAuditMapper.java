package io.datapulse.core.mapper;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface EtlExecutionAuditMapper {

  @Mapping(target = "createdAt", ignore = true)
  EtlExecutionAuditEntity toEntity(EtlExecutionAuditDto dto);

  EtlExecutionAuditDto toDto(EtlExecutionAuditEntity entity);
}
