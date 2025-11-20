package io.datapulse.domain.dto.raw.ozon;

import java.util.List;

public record OzonProductInfoRaw(
    List<OzonAvailabilityRaw> availabilities,
    List<String> barcodes,
    List<String> color_image,
    List<OzonCommissionRaw> commissions,
    String created_at,
    String currency_code,
    Long description_category_id,
    Integer discounted_fbo_stocks,
    List<OzonProductErrorRaw> errors,
    Boolean has_discounted_fbo_item,
    Long id,
    List<String> images,
    List<String> images360,
    Boolean is_archived,
    Boolean is_autoarchived,
    Boolean is_discounted,
    Boolean is_kgt,
    Boolean is_prepayment_allowed,
    Boolean is_super,
    String min_price,
    OzonModelInfoRaw model_info,
    String name,
    String offer_id,
    String old_price,
    String price,
    OzonPriceIndexesRaw price_indexes,
    List<String> primary_image,
    List<OzonPromotionRaw> promotions,
    Long sku,
    List<OzonSourceRaw> sources,
    OzonStatusesRaw statuses,
    OzonStocksWrapperRaw stocks,
    Long type_id,
    String updated_at,
    String vat,
    OzonVisibilityDetailsRaw visibility_details,
    Double volume_weight
) {

  public record OzonAvailabilityRaw(
      String availability,
      List<OzonAvailabilityReasonRaw> reasons,
      Long sku,
      String source
  ) {

  }

  public record OzonAvailabilityReasonRaw(
      OzonHumanTextRaw human_text,
      Long id
  ) {

  }

  public record OzonHumanTextRaw(String text) {

  }

  public record OzonCommissionRaw(
      Double delivery_amount,
      Double percent,
      Double return_amount,
      String sale_schema,
      Double value
  ) {

  }

  public record OzonProductErrorRaw(
      Long attribute_id,
      String code,
      String field,
      String level,
      String state,
      OzonErrorTextsRaw texts
  ) {

  }

  public record OzonErrorTextsRaw(
      String attribute_name,
      String description,
      String hint_code,
      String message,
      List<OzonErrorParamRaw> params,
      String short_description
  ) {

  }

  public record OzonErrorParamRaw(String name, String value) {

  }

  public record OzonModelInfoRaw(Integer count, Long model_id) {

  }

  public record OzonPriceIndexesRaw(
      String color_index,
      OzonPriceIndexDataRaw external_index_data,
      OzonPriceIndexDataRaw ozon_index_data,
      OzonPriceIndexDataRaw self_marketplaces_index_data
  ) {

  }

  public record OzonPriceIndexDataRaw(
      String minimal_price,
      String minimal_price_currency,
      Double price_index_value
  ) {

  }

  public record OzonPromotionRaw(Boolean is_enabled, String type) {

  }

  public record OzonSourceRaw(
      String created_at,
      String quant_code,
      String shipment_type,
      Long sku,
      String source
  ) {

  }

  public record OzonStatusesRaw(
      Boolean is_created,
      String moderate_status,
      String status,
      String status_description,
      String status_failed,
      String status_name,
      String status_tooltip,
      String status_updated_at,
      String validation_status
  ) {

  }

  public record OzonStocksWrapperRaw(
      Boolean has_stock,
      List<OzonStockRaw> stocks
  ) {

  }

  public record OzonStockRaw(
      Integer present,
      Integer reserved,
      Long sku,
      String source
  ) {

  }

  public record OzonVisibilityDetailsRaw(Boolean has_price, Boolean has_stock) {

  }
}
