package io.datapulse.core.mapper.inventory;

import io.datapulse.core.entity.inventory.FactInventorySnapshotEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.dto.inventory.InventorySnapshotDto;
import io.datapulse.domain.dto.request.inventory.InventorySnapshotQueryRequest;
import io.datapulse.domain.dto.response.inventory.InventorySnapshotResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = TimeMapper.class, config = BaseMapperConfig.class)
public interface InventorySnapshotMapper {

  InventorySnapshotDto toDto(FactInventorySnapshotEntity entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "sourcePlatform", expression = "java(request.marketplace().tag())")
  @Mapping(target = "snapshotDate", ignore = true)
  @Mapping(target = "quantityTotal", ignore = true)
  @Mapping(target = "quantityAvailable", ignore = true)
  @Mapping(target = "quantityReserved", ignore = true)
  @Mapping(target = "quantityInWayToClient", ignore = true)
  @Mapping(target = "quantityInWayFromClient", ignore = true)
  @Mapping(target = "quantityReturnToSeller", ignore = true)
  @Mapping(target = "quantityReturnFromCustomer", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  InventorySnapshotDto toDto(InventorySnapshotQueryRequest request);

  InventorySnapshotResponse toResponse(InventorySnapshotDto dto);
}
