package io.datapulse.common.error;

public final class MessageCodes {

    private MessageCodes() {
    }

    // --- General ---
    public static final String INTERNAL_ERROR = "internal.error";
    public static final String VALIDATION_FAILED = "validation.failed";
    public static final String OPERATION_NOT_ALLOWED = "operation.not.allowed";

    // --- Auth ---
    public static final String UNAUTHORIZED = "auth.unauthorized";
    public static final String ACCESS_DENIED = "access.denied";

    // --- Entity lifecycle ---
    public static final String ENTITY_NOT_FOUND = "entity.not.found";
    public static final String DUPLICATE_ENTITY = "entity.duplicate";
    public static final String INVALID_STATE = "entity.invalid.state";

    // --- Workspace / Connection shortcuts ---
    public static final String WORKSPACE_NOT_FOUND = "workspace.not.found";
    public static final String WORKSPACE_MEMBERSHIP_REQUIRED = "workspace.membership.required";
    public static final String WORKSPACE_HEADER_MISSING = "workspace.header.missing";
    public static final String CONNECTION_NOT_FOUND = "connection.not.found";

    // --- User ---
    public static final String USER_DEACTIVATED = "user.deactivated";

    // --- Integration ---
    public static final String RATE_LIMITED = "api.rate.limited";
    public static final String CONNECTION_DUPLICATE = "connection.duplicate";
    public static final String CONNECTION_INVALID_STATE = "connection.invalid.state";
    public static final String CONNECTION_MARKETPLACE_MISMATCH = "connection.marketplace.mismatch";
    public static final String CREDENTIALS_INVALID = "credentials.invalid";
    public static final String VAULT_UNAVAILABLE = "vault.unavailable";

    // --- ETL ---
    public static final String JOB_NOT_FOUND = "job.not.found";
    public static final String JOB_NOT_RETRYABLE = "job.not.retryable";
    public static final String JOB_ACTIVE_EXISTS = "job.active.exists";
    public static final String COST_PROFILE_INVALID = "cost.profile.invalid";
    public static final String COST_PROFILE_BULK_TOO_LARGE = "cost.profile.bulk.too.large";
    public static final String SELLER_SKU_NOT_FOUND = "seller.sku.not.found";

    // --- Audit & Alerting ---
    public static final String AUDIT_LOG_NOT_FOUND = "audit.log.not.found";
    public static final String ALERT_EVENT_NOT_FOUND = "alert.event.not.found";
    public static final String ALERT_EVENT_INVALID_STATE = "alert.event.invalid.state";
    public static final String ALERT_RULE_NOT_FOUND = "alert.rule.not.found";

    // --- Pricing: Guards ---
    public static final String PRICING_GUARD_MANUAL_LOCK = "pricing.guard.manual_lock";
    public static final String PRICING_GUARD_STALE_DATA_UNKNOWN = "pricing.guard.stale_data.unknown";
    public static final String PRICING_GUARD_STALE_DATA_STALE = "pricing.guard.stale_data.stale";
    public static final String PRICING_GUARD_STOCK_OUT = "pricing.guard.stock_out";
    public static final String PRICING_GUARD_MARGIN_NEGATIVE = "pricing.guard.margin_negative";
    public static final String PRICING_GUARD_FREQUENCY = "pricing.guard.frequency";
    public static final String PRICING_GUARD_VOLATILITY = "pricing.guard.volatility";
    public static final String PRICING_GUARD_PROMO_ACTIVE = "pricing.guard.promo_active";

    // --- Pricing: Strategy skip/hold reasons ---
    public static final String PRICING_COGS_MISSING = "pricing.strategy.cogs_missing";
    public static final String PRICING_COMMISSION_MISSING = "pricing.strategy.commission_missing";
    public static final String PRICING_DENOMINATOR_INVALID = "pricing.strategy.denominator_invalid";
    public static final String PRICING_CURRENT_PRICE_MISSING = "pricing.strategy.current_price_missing";
    public static final String PRICING_NO_CHANGE = "pricing.strategy.no_change";

    // --- Pricing: Policy API ---
    public static final String PRICING_POLICY_NOT_FOUND = "pricing.policy.not_found";
    public static final String PRICING_POLICY_ARCHIVED = "pricing.policy.archived";
    public static final String PRICING_POLICY_INVALID_STATE = "pricing.policy.invalid_state";
    public static final String PRICING_ASSIGNMENT_DUPLICATE = "pricing.assignment.duplicate";
    public static final String PRICING_LOCK_ALREADY_EXISTS = "pricing.lock.already_exists";
    public static final String PRICING_RUN_ALREADY_IN_PROGRESS = "pricing.run.already_in_progress";
    public static final String PRICING_BULK_DUPLICATE = "pricing.bulk.duplicate";
    public static final String PRICING_BULK_TOO_LARGE = "pricing.bulk.too_large";

    // --- Execution ---
    public static final String EXECUTION_ACTION_NOT_FOUND = "execution.action.not_found";
    public static final String EXECUTION_ACTION_INVALID_TRANSITION = "execution.action.invalid_transition";
    public static final String EXECUTION_ACTION_CAS_CONFLICT = "execution.action.cas_conflict";
    public static final String EXECUTION_ACTION_NOT_CANCELLABLE = "execution.action.not_cancellable";
    public static final String EXECUTION_ACTION_ALREADY_ACTIVE = "execution.action.already_active";
    public static final String EXECUTION_ACTION_NOT_RETRIABLE = "execution.action.not_retriable";
    public static final String EXECUTION_RECONCILIATION_FAILED = "execution.reconciliation.failed";
    public static final String EXECUTION_RECONCILIATION_MISMATCH = "execution.reconciliation.mismatch";
    public static final String EXECUTION_RECONCILIATION_INVALID_OUTCOME = "execution.reconciliation.invalid_outcome";
    public static final String EXECUTION_STUCK_STATE_DETECTED = "execution.stuck_state.detected";
    public static final String EXECUTION_POISON_PILL = "execution.poison_pill.detected";

    // --- Execution: Simulation ---
    public static final String EXECUTION_SIMULATION_RESET_SUCCESS = "execution.simulation.reset_success";
    public static final String EXECUTION_SIMULATION_NO_DATA = "execution.simulation.no_data";

    // --- Promotions: Policy ---
    public static final String PROMO_POLICY_NOT_FOUND = "promo.policy.not_found";
    public static final String PROMO_POLICY_ARCHIVED = "promo.policy.archived";
    public static final String PROMO_POLICY_INVALID_STATE = "promo.policy.invalid_state";
    public static final String PROMO_ASSIGNMENT_DUPLICATE = "promo.assignment.duplicate";

    // --- Promotions: Evaluation ---
    public static final String PROMO_RUN_ALREADY_IN_PROGRESS = "promo.run.already_in_progress";
    public static final String PROMO_RUN_NOT_FOUND = "promo.run.not_found";

    // --- Promotions: Action ---
    public static final String PROMO_ACTION_NOT_FOUND = "promo.action.not_found";
    public static final String PROMO_ACTION_INVALID_TRANSITION = "promo.action.invalid_transition";
    public static final String PROMO_ACTION_CAS_CONFLICT = "promo.action.cas_conflict";
    public static final String PROMO_ACTION_NOT_CANCELLABLE = "promo.action.not_cancellable";

    // --- Promotions: Product ---
    public static final String PROMO_PRODUCT_NOT_FOUND = "promo.product.not_found";
    public static final String PROMO_PRODUCT_NOT_ELIGIBLE = "promo.product.not_eligible";
    public static final String PROMO_PRODUCT_NOT_PARTICIPATING = "promo.product.not_participating";
    public static final String PROMO_CAMPAIGN_FROZEN = "promo.campaign.frozen";

    // --- Seller Operations ---
    public static final String GRID_EXPORT_TOO_MANY_ROWS = "grid.export.too_many_rows";
    public static final String SAVED_VIEW_NOT_FOUND = "saved_view.not_found";
    public static final String SAVED_VIEW_SYSTEM_READONLY = "saved_view.system_readonly";

    // --- Seller Operations: Working Queues ---
    public static final String QUEUE_NOT_FOUND = "queue.not_found";
    public static final String QUEUE_ITEM_NOT_FOUND = "queue.item.not_found";
    public static final String QUEUE_ITEM_INVALID_STATE = "queue.item.invalid_state";
    public static final String QUEUE_DUPLICATE = "queue.duplicate";
}
