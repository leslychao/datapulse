package io.datapulse.etl.dim.warehouse;

import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonClusterListRaw.OzonWarehouseRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseSellerListRaw;
import java.util.stream.Stream;

public interface WarehouseRawReader {

  Stream<OzonWarehouseFbsListRaw> streamOzonFbs(Long accountId, String requestId);

  Stream<OzonWarehouseRaw> streamOzonFboWarehouses(Long accountId, String requestId);

  Stream<WbWarehouseFbwListRaw> streamWbFbw(Long accountId, String requestId);

  Stream<WbOfficeFbsListRaw> streamWbFbsOffices(Long accountId, String requestId);

  Stream<WbWarehouseSellerListRaw> streamWbSellerWarehouses(Long accountId, String requestId);
}
