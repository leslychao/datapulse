package io.datapulse.etl.dim.warehouse;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimWarehouseJdbcRepository implements DimWarehouseRepository {

  private static final String UPSERT_SQL = """
      insert into dim_warehouses (marketplace, warehouse_role, warehouse_id, name, active, fbs)
      values (?, ?, ?, ?, ?, ?)
      on conflict (marketplace, warehouse_role, warehouse_id) do update
        set name = excluded.name,
            active = excluded.active,
            fbs = excluded.fbs
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void saveAll(Collection<DimWarehouse> warehouses) {
    if (warehouses.isEmpty()) {
      return;
    }

    jdbcTemplate.batchUpdate(
        UPSERT_SQL,
        warehouses,
        warehouses.size(),
        (ps, warehouse) -> {
          ps.setString(1, warehouse.marketplace().name());
          ps.setString(2, warehouse.warehouseRole());
          ps.setLong(3, warehouse.warehouseId());
          ps.setString(4, warehouse.name());
          ps.setBoolean(5, warehouse.active());
          ps.setBoolean(6, warehouse.fbs());
        }
    );
  }
}
