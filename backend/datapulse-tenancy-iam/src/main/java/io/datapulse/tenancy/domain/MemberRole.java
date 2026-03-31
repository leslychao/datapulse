package io.datapulse.tenancy.domain;

import java.util.EnumSet;
import java.util.Set;

public enum MemberRole {

    OWNER,
    ADMIN,
    PRICING_MANAGER,
    OPERATOR,
    ANALYST,
    VIEWER;

    private static final Set<MemberRole> WRITE_ROLES = EnumSet.of(OWNER, ADMIN);
    private static final Set<MemberRole> PRICING_ROLES = EnumSet.of(OWNER, ADMIN, PRICING_MANAGER);
    private static final Set<MemberRole> OPERATOR_ROLES = EnumSet.of(OWNER, ADMIN, PRICING_MANAGER, OPERATOR);

    public static Set<MemberRole> writeRoles() {
        return WRITE_ROLES;
    }

    public static Set<MemberRole> pricingRoles() {
        return PRICING_ROLES;
    }

    public static Set<MemberRole> operatorRoles() {
        return OPERATOR_ROLES;
    }

    public boolean isAtLeast(MemberRole required) {
        return this.ordinal() <= required.ordinal();
    }
}
