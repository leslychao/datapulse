package io.datapulse.marketplaces.dto.raw.warehouse.wb;

public record WbOfficeFbsListRaw(
    String address,
    String name,
    String city,
    Long id,
    Double longitude,
    Double latitude,
    Integer cargoType,
    Integer deliveryType,
    String federalDistrict,
    Boolean selected
) {

}
