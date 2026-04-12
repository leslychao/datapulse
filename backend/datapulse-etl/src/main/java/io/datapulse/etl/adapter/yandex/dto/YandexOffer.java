package io.datapulse.etl.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexOffer(
    String offerId,
    String name,
    String vendor,
    List<String> barcodes,
    String vendorCode,
    String description,
    YandexWeightDimensions weightDimensions,
    YandexBasicPrice basicPrice,
    YandexBasicPrice purchasePrice,
    String cardStatus,
    List<YandexOfferCampaign> campaigns,
    List<YandexSellingProgram> sellingPrograms,
    boolean archived,
    String groupId
) {}
