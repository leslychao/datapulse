package io.datapulse.etl.domain;

import java.util.EnumSet;
import java.util.Set;

public enum EtlEventType {
    CATEGORY_DICT,
    WAREHOUSE_DICT,
    PRODUCT_DICT,
    PRICE_SNAPSHOT,
    INVENTORY_FACT,
    SUPPLY_FACT,
    SALES_FACT,
    FACT_FINANCE,
    PROMO_SYNC,
    ADVERTISING_FACT;

    private static final Set<EtlEventType> STATE_EVENTS = EnumSet.of(
            CATEGORY_DICT, WAREHOUSE_DICT, PRODUCT_DICT,
            PRICE_SNAPSHOT, INVENTORY_FACT, SUPPLY_FACT, PROMO_SYNC
    );

    private static final Set<EtlEventType> FLOW_EVENTS = EnumSet.of(
            SALES_FACT, ADVERTISING_FACT
    );

    private static final Set<EtlEventType> FINANCE_EVENTS = EnumSet.of(
            FACT_FINANCE
    );

    public RetentionCategory retentionCategory() {
        if (FINANCE_EVENTS.contains(this)) {
            return RetentionCategory.FINANCE;
        }
        if (FLOW_EVENTS.contains(this)) {
            return RetentionCategory.FLOW;
        }
        return RetentionCategory.STATE;
    }

    public static Set<EtlEventType> stateEvents() {
        return STATE_EVENTS;
    }

    public static Set<EtlEventType> flowEvents() {
        return FLOW_EVENTS;
    }

    public static Set<EtlEventType> financeEvents() {
        return FINANCE_EVENTS;
    }
}
