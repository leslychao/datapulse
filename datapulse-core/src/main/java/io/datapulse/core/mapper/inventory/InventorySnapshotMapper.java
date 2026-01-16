package io.datapulse.core.mapper.inventory;

import io.datapulse.core.entity.inventory.FactInventorySnapshotEntity;
import io.datapulse.core.mapper.BaseMapperConfig;
import io.datapulse.core.mapper.TimeMapper;
import io.datapulse.domain.response.inventory.InventorySnapshotResponse;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    uses = TimeMapper.class,
    config = BaseMapperConfig.class
)
public interface InventorySnapshotMapper {

  InventorySnapshotResponse toResponse(FactInventorySnapshotEntity entity);
}
