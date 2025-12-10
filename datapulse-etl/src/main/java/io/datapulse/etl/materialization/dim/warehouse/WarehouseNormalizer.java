package io.datapulse.etl.materialization.dim.warehouse;

import io.datapulse.marketplaces.dto.raw.ozon.OzonClusterListRaw.OzonWarehouseRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseSellerListRaw;

public interface WarehouseNormalizer {

  DimWarehouse fromOzonFbs(OzonWarehouseFbsListRaw source);

  DimWarehouse fromOzonFbo(OzonWarehouseRaw source);

  DimWarehouse fromWbFbw(WbWarehouseFbwListRaw source);

  DimWarehouse fromWbFbsOffice(WbOfficeFbsListRaw source);

  DimWarehouse fromWbSeller(WbWarehouseSellerListRaw source);
}
