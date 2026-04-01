package io.datapulse.pricing.domain;

public enum ScopeType {
    CONNECTION,
    CATEGORY,
    SKU;

    public int specificity() {
        return switch (this) {
            case CONNECTION -> 1;
            case CATEGORY -> 2;
            case SKU -> 3;
        };
    }
}
