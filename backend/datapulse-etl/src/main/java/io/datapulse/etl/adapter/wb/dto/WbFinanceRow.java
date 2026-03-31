package io.datapulse.etl.adapter.wb.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WB reportDetailByPeriod v5 row. Composite row model (DD-8):
 * one row contains multiple financial dimensions simultaneously.
 * Nullable wrapper types for optional v5 fields (DD-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WbFinanceRow(
        @JsonProperty("realizationreport_id") long realizationreportId,
        @JsonProperty("rrd_id") long rrdId,
        @JsonProperty("rr_dt") String rrDt,
        @JsonProperty("date_from") String dateFrom,
        @JsonProperty("date_to") String dateTo,
        @JsonProperty("create_dt") String createDt,
        @JsonProperty("order_dt") String orderDt,
        @JsonProperty("sale_dt") String saleDt,
        String srid,
        @JsonProperty("nm_id") long nmId,
        @JsonProperty("sa_name") String saName,
        String barcode,
        @JsonProperty("doc_type_name") String docTypeName,
        @JsonProperty("supplier_oper_name") String supplierOperName,
        int quantity,
        @JsonProperty("retail_price") BigDecimal retailPrice,
        @JsonProperty("retail_amount") BigDecimal retailAmount,
        @JsonProperty("retail_price_withdisc_rub") BigDecimal retailPriceWithdiscRub,
        @JsonProperty("ppvz_for_pay") BigDecimal ppvzForPay,
        @JsonProperty("ppvz_vw") BigDecimal ppvzVw,
        @JsonProperty("ppvz_vw_nds") BigDecimal ppvzVwNds,
        @JsonProperty("ppvz_sales_commission") BigDecimal ppvzSalesCommission,
        @JsonProperty("delivery_rub") BigDecimal deliveryRub,
        @JsonProperty("penalty") BigDecimal penalty,
        @JsonProperty("storage_fee") BigDecimal storageFee,
        @JsonProperty("deduction") BigDecimal deduction,
        @JsonProperty("additional_payment") BigDecimal additionalPayment,
        @JsonProperty("rebill_logistic_cost") BigDecimal rebillLogisticCost,
        @JsonProperty("acquiring_fee") BigDecimal acquiringFee,
        @JsonProperty("acquiring_percent") BigDecimal acquiringPercent,
        @JsonProperty("acceptance") BigDecimal acceptance,
        @JsonProperty("ppvz_spp_prc") BigDecimal ppvzSppPrc,
        @JsonProperty("ppvz_office_id") Long ppvzOfficeId,
        @JsonProperty("office_name") String officeName,
        @JsonProperty("gi_id") Long giId,
        @JsonProperty("shk_id") Long shkId,
        @JsonProperty("assembly_id") Long assemblyId,
        @JsonProperty("order_uid") String orderUid,
        @JsonProperty("sticker_id") String stickerId,
        @JsonProperty("site_country") String siteCountry,
        @JsonProperty("delivery_amount") Integer deliveryAmount,
        @JsonProperty("return_amount") Integer returnAmount,
        @JsonProperty("dlv_prc") BigDecimal dlvPrc,
        @JsonProperty("srv_dbs") Boolean srvDbs,
        @JsonProperty("is_legal_entity") Boolean isLegalEntity,
        @JsonProperty("trbx_id") String trbxId,
        @JsonProperty("fix_tariff_date_from") String fixTariffDateFrom,
        @JsonProperty("fix_tariff_date_to") String fixTariffDateTo,
        @JsonProperty("installment_cofinancing_amount") BigDecimal installmentCofinancingAmount,
        @JsonProperty("wibes_wb_discount_percent") BigDecimal wibesWbDiscountPercent,
        @JsonProperty("cashback_amount") BigDecimal cashbackAmount,
        @JsonProperty("cashback_discount") BigDecimal cashbackDiscount,
        @JsonProperty("cashback_commission_change") BigDecimal cashbackCommissionChange,
        @JsonProperty("seller_promo_discount") BigDecimal sellerPromoDiscount,
        @JsonProperty("loyalty_discount") BigDecimal loyaltyDiscount,
        @JsonProperty("currency_name") String currencyName,
        @JsonProperty("delivery_method") String deliveryMethod,
        @JsonProperty("seller_promo_id") Long sellerPromoId,
        @JsonProperty("loyalty_id") Long loyaltyId,
        @JsonProperty("kiz") String kiz,
        @JsonProperty("report_type") Integer reportType,
        @JsonProperty("payment_schedule") Integer paymentSchedule,
        @JsonProperty("uuid_promocode") String uuidPromocode,
        @JsonProperty("sale_price_promocode_discount_prc") BigDecimal salePricePromocodeDiscountPrc
) {}
