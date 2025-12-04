package io.datapulse.etl.dim.warehouse;

import java.util.Collection;

public interface DimWarehouseRepository {

  void saveAll(Collection<DimWarehouse> warehouses);
}
