package io.datapulse.marketplaces.dto.raw.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbSalesReportDetailRowRaw(

    @SerializedName("realizationreport_id")
    @JsonProperty("realizationreport_id")
    Long realizationReportId,

    @SerializedName("date_from")
    @JsonProperty("date_from")
    String dateFrom,

    @SerializedName("date_to")
    @JsonProperty("date_to")
    String dateTo,

    @SerializedName("create_dt")
    @JsonProperty("create_dt")
    String createDt,

    @SerializedName("currency_name")
    @JsonProperty("currency_name")
    String currencyName,

    @SerializedName("suppliercontract_code")
    @JsonProperty("suppliercontract_code")
    String supplierContractCode,

    @SerializedName("rrd_id")
    @JsonProperty("rrd_id")
    Long rrdId,

    @SerializedName("gi_id")
    @JsonProperty("gi_id")
    Long giId,

    @SerializedName("dlv_prc")
    @JsonProperty("dlv_prc")
    Double dlvPrc,

    @SerializedName("fix_tariff_date_from")
    @JsonProperty("fix_tariff_date_from")
    String fixTariffDateFrom,

    @SerializedName("fix_tariff_date_to")
    @JsonProperty("fix_tariff_date_to")
    String fixTariffDateTo,

    @SerializedName("subject_name")
    @JsonProperty("subject_name")
    String subjectName,

    @SerializedName("nm_id")
    @JsonProperty("nm_id")
    Long nmId,

    @SerializedName("brand_name")
    @JsonProperty("brand_name")
    String brandName,

    @SerializedName("sa_name")
    @JsonProperty("sa_name")
    String saName,

    @SerializedName("ts_name")
    @JsonProperty("ts_name")
    String tsName,

    @SerializedName("barcode")
    @JsonProperty("barcode")
    String barcode,

    @SerializedName("doc_type_name")
    @JsonProperty("doc_type_name")
    String docTypeName,

    @SerializedName("quantity")
    @JsonProperty("quantity")
    Integer quantity,

    @SerializedName("retail_price")
    @JsonProperty("retail_price")
    Double retailPrice,

    @SerializedName("retail_amount")
    @JsonProperty("retail_amount")
    Double retailAmount,

    @SerializedName("sale_percent")
    @JsonProperty("sale_percent")
    Double salePercent,

    @SerializedName("commission_percent")
    @JsonProperty("commission_percent")
    Double commissionPercent,

    @SerializedName("office_name")
    @JsonProperty("office_name")
    String officeName,

    @SerializedName("supplier_oper_name")
    @JsonProperty("supplier_oper_name")
    String supplierOperName,

    @SerializedName("order_dt")
    @JsonProperty("order_dt")
    String orderDt,

    @SerializedName("sale_dt")
    @JsonProperty("sale_dt")
    String saleDt,

    @SerializedName("rr_dt")
    @JsonProperty("rr_dt")
    String rrDt,

    @SerializedName("shk_id")
    @JsonProperty("shk_id")
    Long shkId,

    @SerializedName("retail_price_withdisc_rub")
    @JsonProperty("retail_price_withdisc_rub")
    Double retailPriceWithdiscRub,

    @SerializedName("delivery_amount")
    @JsonProperty("delivery_amount")
    Double deliveryAmount,

    @SerializedName("return_amount")
    @JsonProperty("return_amount")
    Double returnAmount,

    @SerializedName("delivery_rub")
    @JsonProperty("delivery_rub")
    Double deliveryRub,

    @SerializedName("gi_box_type_name")
    @JsonProperty("gi_box_type_name")
    String giBoxTypeName,

    @SerializedName("product_discount_for_report")
    @JsonProperty("product_discount_for_report")
    Double productDiscountForReport,

    @SerializedName("supplier_promo")
    @JsonProperty("supplier_promo")
    Double supplierPromo,

    @SerializedName("ppvz_spp_prc")
    @JsonProperty("ppvz_spp_prc")
    Double ppvzSppPrc,

    @SerializedName("ppvz_kvw_prc_base")
    @JsonProperty("ppvz_kvw_prc_base")
    Double ppvzKvwPrcBase,

    @SerializedName("ppvz_kvw_prc")
    @JsonProperty("ppvz_kvw_prc")
    Double ppvzKvwPrc,

    @SerializedName("sup_rating_prc_up")
    @JsonProperty("sup_rating_prc_up")
    Double supRatingPrcUp,

    @SerializedName("is_kgvp_v2")
    @JsonProperty("is_kgvp_v2")
    Integer isKgvpV2,

    @SerializedName("ppvz_sales_commission")
    @JsonProperty("ppvz_sales_commission")
    Double ppvzSalesCommission,

    @SerializedName("ppvz_for_pay")
    @JsonProperty("ppvz_for_pay")
    Double ppvzForPay,

    @SerializedName("ppvz_reward")
    @JsonProperty("ppvz_reward")
    Double ppvzReward,

    @SerializedName("acquiring_fee")
    @JsonProperty("acquiring_fee")
    Double acquiringFee,

    @SerializedName("acquiring_percent")
    @JsonProperty("acquiring_percent")
    Double acquiringPercent,

    @SerializedName("payment_processing")
    @JsonProperty("payment_processing")
    String paymentProcessing,

    @SerializedName("acquiring_bank")
    @JsonProperty("acquiring_bank")
    String acquiringBank,

    @SerializedName("ppvz_vw")
    @JsonProperty("ppvz_vw")
    Double ppvzVw,

    @SerializedName("ppvz_vw_nds")
    @JsonProperty("ppvz_vw_nds")
    Double ppvzVwNds,

    @SerializedName("ppvz_office_name")
    @JsonProperty("ppvz_office_name")
    String ppvzOfficeName,

    @SerializedName("ppvz_office_id")
    @JsonProperty("ppvz_office_id")
    Long ppvzOfficeId,

    @SerializedName("ppvz_supplier_id")
    @JsonProperty("ppvz_supplier_id")
    Long ppvzSupplierId,

    @SerializedName("ppvz_supplier_name")
    @JsonProperty("ppvz_supplier_name")
    String ppvzSupplierName,

    @SerializedName("ppvz_inn")
    @JsonProperty("ppvz_inn")
    String ppvzInn,

    @SerializedName("declaration_number")
    @JsonProperty("declaration_number")
    String declarationNumber,

    @SerializedName("bonus_type_name")
    @JsonProperty("bonus_type_name")
    String bonusTypeName,

    @SerializedName("sticker_id")
    @JsonProperty("sticker_id")
    String stickerId,

    @SerializedName("site_country")
    @JsonProperty("site_country")
    String siteCountry,

    @SerializedName("srv_dbs")
    @JsonProperty("srv_dbs")
    Boolean srvDbs,

    @SerializedName("penalty")
    @JsonProperty("penalty")
    Double penalty,

    @SerializedName("additional_payment")
    @JsonProperty("additional_payment")
    Double additionalPayment,

    @SerializedName("rebill_logistic_cost")
    @JsonProperty("rebill_logistic_cost")
    Double rebillLogisticCost,

    @SerializedName("rebill_logistic_org")
    @JsonProperty("rebill_logistic_org")
    String rebillLogisticOrg,

    @SerializedName("storage_fee")
    @JsonProperty("storage_fee")
    Double storageFee,

    @SerializedName("deduction")
    @JsonProperty("deduction")
    Double deduction,

    @SerializedName("acceptance")
    @JsonProperty("acceptance")
    Double acceptance,

    @SerializedName("assembly_id")
    @JsonProperty("assembly_id")
    Long assemblyId,

    @SerializedName("kiz")
    @JsonProperty("kiz")
    String kiz,

    @SerializedName("srid")
    @JsonProperty("srid")
    String srid,

    @SerializedName("report_type")
    @JsonProperty("report_type")
    Integer reportType,

    @SerializedName("is_legal_entity")
    @JsonProperty("is_legal_entity")
    Boolean isLegalEntity,

    @SerializedName("trbx_id")
    @JsonProperty("trbx_id")
    String trbxId,

    @SerializedName("installment_cofinancing_amount")
    @JsonProperty("installment_cofinancing_amount")
    Double installmentCofinancingAmount,

    @SerializedName("wibes_wb_discount_percent")
    @JsonProperty("wibes_wb_discount_percent")
    Double wibesWbDiscountPercent,

    @SerializedName("cashback_amount")
    @JsonProperty("cashback_amount")
    Double cashbackAmount,

    @SerializedName("cashback_discount")
    @JsonProperty("cashback_discount")
    Double cashbackDiscount,

    @SerializedName("cashback_commission_change")
    @JsonProperty("cashback_commission_change")
    Double cashbackCommissionChange,

    @SerializedName("order_uid")
    @JsonProperty("order_uid")
    String orderUid,

    @SerializedName("payment_schedule")
    @JsonProperty("payment_schedule")
    Integer paymentSchedule

) {}
