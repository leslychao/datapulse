package io.datapulse.core.mapper;

import io.datapulse.core.entity.EtlSyncAuditEntity;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface EtlSyncAuditMapper {

  EtlSyncAuditEntity toEntity(EtlSyncAuditDto dto);

  EtlSyncAuditDto toDto(EtlSyncAuditEntity entity);
}
