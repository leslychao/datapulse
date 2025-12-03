package io.datapulse.marketplaces.dto.raw.ozon;

import java.util.List;

public record OzonLogisticClustersRaw(
    long id,
    String name,
    String type,
    List<OzonLogisticClusterRaw> logistic_clusters
) {

  public record OzonLogisticClusterRaw(
      List<OzonWarehouseRaw> warehouses
  ) {

  }

  public record OzonWarehouseRaw(
      String name,
      String type,
      long warehouse_id
  ) {

  }
}
