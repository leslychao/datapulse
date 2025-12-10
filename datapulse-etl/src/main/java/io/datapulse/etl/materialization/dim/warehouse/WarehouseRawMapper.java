package io.datapulse.etl.materialization.dim.warehouse;

import io.datapulse.marketplaces.dto.raw.ozon.OzonClusterListRaw.OzonWarehouseRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseSellerListRaw;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface WarehouseRawMapper extends WarehouseNormalizer {

  @Override
  @Mapping(target = "marketplace", constant = "OZON")
  @Mapping(target = "warehouseRole", constant = "OZON_FBS")
  @Mapping(target = "warehouseId", source = "warehouse_id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "active", source = "status", qualifiedByName = "ozonActive")
  @Mapping(target = "fbs", constant = "true")
  DimWarehouse fromOzonFbs(OzonWarehouseFbsListRaw source);

  @Override
  @Mapping(target = "marketplace", constant = "OZON")
  @Mapping(target = "warehouseRole", constant = "OZON_FBO")
  @Mapping(target = "warehouseId", source = "warehouse_id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "active", constant = "true")
  @Mapping(target = "fbs", constant = "false")
  DimWarehouse fromOzonFbo(OzonWarehouseRaw source);

  @Override
  @Mapping(target = "marketplace", constant = "WILDBERRIES")
  @Mapping(target = "warehouseRole", constant = "WB_FBW")
  @Mapping(target = "warehouseId", source = "id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "active", source = "active")
  @Mapping(target = "fbs", constant = "false")
  DimWarehouse fromWbFbw(WbWarehouseFbwListRaw source);

  @Override
  @Mapping(target = "marketplace", constant = "WILDBERRIES")
  @Mapping(target = "warehouseRole", constant = "WB_FBS_OFFICE")
  @Mapping(target = "warehouseId", source = "id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "active", source = "selected")
  @Mapping(target = "fbs", constant = "true")
  DimWarehouse fromWbFbsOffice(WbOfficeFbsListRaw source);

  @Override
  @Mapping(target = "marketplace", constant = "WILDBERRIES")
  @Mapping(target = "warehouseRole", constant = "WB_SELLER")
  @Mapping(target = "warehouseId", source = "id")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "active", source = "isDeleting", qualifiedByName = "wbActive")
  @Mapping(target = "fbs", source = "deliveryType", qualifiedByName = "wbDeliveryFbs")
  DimWarehouse fromWbSeller(WbWarehouseSellerListRaw source);

  @Named("ozonActive")
  default boolean mapOzonStatus(String status) {
    return "created".equalsIgnoreCase(status) || "working".equalsIgnoreCase(status);
  }

  @Named("wbActive")
  default boolean mapWbActive(Boolean isDeleting) {
    return isDeleting == null || !isDeleting;
  }

  @Named("wbDeliveryFbs")
  default boolean mapDelivery(Integer type) {
    return type != null && type == 1;
  }
}
