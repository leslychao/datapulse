package io.datapulse.integration.api;

import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.platform.config.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface ConnectionMapper {

    ConnectionSummaryResponse toSummary(MarketplaceConnectionEntity entity);

    @Mapping(target = "syncStates", source = "syncStates")
    ConnectionResponse toResponse(MarketplaceConnectionEntity entity, List<MarketplaceSyncStateEntity> syncStates);

    SyncStateResponse toSyncState(MarketplaceSyncStateEntity entity);

    List<SyncStateResponse> toSyncStates(List<MarketplaceSyncStateEntity> entities);

    List<ConnectionSummaryResponse> toSummaries(List<MarketplaceConnectionEntity> entities);
}
