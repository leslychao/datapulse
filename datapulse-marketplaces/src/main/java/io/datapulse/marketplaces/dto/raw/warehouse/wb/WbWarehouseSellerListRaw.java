package io.datapulse.marketplaces.dto.raw.warehouse.wb;

public record WbWarehouseSellerListRaw(
    String name,
    Long officeId,
    Long id,
    Integer cargoType,
    Integer deliveryType,
    Boolean isDeleting,
    Boolean isProcessing
) {

}
