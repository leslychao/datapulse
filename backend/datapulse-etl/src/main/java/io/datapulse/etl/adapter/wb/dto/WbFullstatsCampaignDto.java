package io.datapulse.etl.adapter.wb.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbFullstatsCampaignDto(
    long advertId,
    List<Day> days
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Day(
      String date,
      List<App> apps
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record App(
      int appType,
      List<Nm> nms
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Nm(
      long nmId,
      long views,
      long clicks,
      BigDecimal sum,
      int orders,
      double cr,
      int shks,
      @JsonProperty("sum_price") BigDecimal sumPrice,
      int canceled
  ) {}
}
