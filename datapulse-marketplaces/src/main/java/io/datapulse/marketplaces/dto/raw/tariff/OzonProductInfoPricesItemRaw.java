package io.datapulse.marketplaces.dto.raw.tariff;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OzonProductInfoPricesItemRaw {

  @SerializedName("product_id")
  @JsonProperty("product_id")
  private Long productId;

  @SerializedName("offer_id")
  @JsonProperty("offer_id")
  private String offerId;

  @SerializedName("volume_weight")
  @JsonProperty("volume_weight")
  private Double volumeWeight;

  @SerializedName("price")
  @JsonProperty("price")
  private PriceRaw price;

  @SerializedName("commissions")
  @JsonProperty("commissions")
  private CommissionsRaw commissions;

  // -------------------------------------------------------------------------
  // Nested DTOs
  // -------------------------------------------------------------------------

  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class PriceRaw {

    @SerializedName("currency_code")
    @JsonProperty("currency_code")
    private String currencyCode;

    @SerializedName("vat")
    @JsonProperty("vat")
    private Double vat;

    @SerializedName("old_price")
    @JsonProperty("old_price")
    private Double oldPrice;

    @SerializedName("price")
    @JsonProperty("price")
    private Double price;

    @SerializedName("net_price")
    @JsonProperty("net_price")
    private Double netPrice;

    @SerializedName("min_price")
    @JsonProperty("min_price")
    private Double minPrice;

    @SerializedName("retail_price")
    @JsonProperty("retail_price")
    private Double retailPrice;

    @SerializedName("marketing_seller_price")
    @JsonProperty("marketing_seller_price")
    private Double marketingSellerPrice;

    @SerializedName("auto_action_enabled")
    @JsonProperty("auto_action_enabled")
    private Boolean autoActionEnabled;

    @SerializedName("auto_add_to_ozon_actions_list_enabled")
    @JsonProperty("auto_add_to_ozon_actions_list_enabled")
    private Boolean autoAddToOzonActionsListEnabled;
  }

  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class CommissionsRaw {

    @SerializedName("sales_percent_fbo")
    @JsonProperty("sales_percent_fbo")
    private Double salesPercentFbo;

    @SerializedName("sales_percent_fbs")
    @JsonProperty("sales_percent_fbs")
    private Double salesPercentFbs;

    @SerializedName("sales_percent_rfbs")
    @JsonProperty("sales_percent_rfbs")
    private Double salesPercentRfbs;

    @SerializedName("sales_percent_fbp")
    @JsonProperty("sales_percent_fbp")
    private Double salesPercentFbp;

    @SerializedName("fbo_deliv_to_customer_amount")
    @JsonProperty("fbo_deliv_to_customer_amount")
    private Double fboDelivToCustomerAmount;

    @SerializedName("fbo_direct_flow_trans_min_amount")
    @JsonProperty("fbo_direct_flow_trans_min_amount")
    private Double fboDirectFlowTransMinAmount;

    @SerializedName("fbo_direct_flow_trans_max_amount")
    @JsonProperty("fbo_direct_flow_trans_max_amount")
    private Double fboDirectFlowTransMaxAmount;

    @SerializedName("fbo_return_flow_amount")
    @JsonProperty("fbo_return_flow_amount")
    private Double fboReturnFlowAmount;

    @SerializedName("fbs_deliv_to_customer_amount")
    @JsonProperty("fbs_deliv_to_customer_amount")
    private Double fbsDelivToCustomerAmount;

    @SerializedName("fbs_direct_flow_trans_min_amount")
    @JsonProperty("fbs_direct_flow_trans_min_amount")
    private Double fbsDirectFlowTransMinAmount;

    @SerializedName("fbs_direct_flow_trans_max_amount")
    @JsonProperty("fbs_direct_flow_trans_max_amount")
    private Double fbsDirectFlowTransMaxAmount;

    @SerializedName("fbs_first_mile_min_amount")
    @JsonProperty("fbs_first_mile_min_amount")
    private Double fbsFirstMileMinAmount;

    @SerializedName("fbs_first_mile_max_amount")
    @JsonProperty("fbs_first_mile_max_amount")
    private Double fbsFirstMileMaxAmount;

    @SerializedName("fbs_return_flow_amount")
    @JsonProperty("fbs_return_flow_amount")
    private Double fbsReturnFlowAmount;
  }
}
