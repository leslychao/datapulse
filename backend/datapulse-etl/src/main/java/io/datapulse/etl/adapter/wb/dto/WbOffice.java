package io.datapulse.etl.adapter.wb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbOffice(
        long id,
        String name,
        String address,
        String city,
        String federalDistrict,
        double longitude,
        double latitude,
        int cargoType,
        int deliveryType,
        boolean selected
) {}
