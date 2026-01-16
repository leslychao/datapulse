package io.datapulse.domain.response.productcost;

import java.util.List;

public record ProductCostImportResponse(
    long importedRows,
    long skippedNotFoundRows,
    List<ProductCostNotFoundRow> notFound
) {

  public record ProductCostNotFoundRow(
      int rowNumber,
      String sourceProductId
  ) {

  }
}
