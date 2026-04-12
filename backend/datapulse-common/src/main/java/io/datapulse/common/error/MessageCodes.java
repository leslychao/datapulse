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

    // --- Tenant ---
    public static final String TENANT_NOT_FOUND = "tenant.not.found";
    public static final String TENANT_LIMIT_EXCEEDED = "tenant.limit.exceeded";
    public static final String TENANT_NOT_OWNER = "tenant.not.owner";

    // --- Workspace / Connection shortcuts ---
    public static final String WORKSPACE_NOT_FOUND = "workspace.not.found";
    public static final String WORKSPACE_MEMBERSHIP_REQUIRED = "workspace.membership.required";
    public static final String WORKSPACE_HEADER_MISSING = "workspace.header.missing";
    public static final String WORKSPACE_NOT_ACTIVE = "workspace.not.active";
    public static final String WORKSPACE_NOT_SUSPENDED = "workspace.not.suspended";
    public static final String WORKSPACE_ALREADY_ARCHIVED = "workspace.already.archived";
    public static final String CONNECTION_NOT_FOUND = "connection.not.found";

    // --- User ---
    public static final String USER_DEACTIVATED = "user.deactivated";

    // --- Members ---
    public static final String MEMBER_ROLE_CANNOT_CHANGE_OWNER = "member.role.cannot.change.owner";
    public static final String MEMBER_ROLE_CANNOT_CHANGE_SELF = "member.role.cannot.change.self";
    public static final String MEMBER_ROLE_CANNOT_ASSIGN_OWNER = "member.role.cannot.assign.owner";
    public static final String MEMBER_CANNOT_REMOVE_OWNER = "member.cannot.remove.owner";
    public static final String MEMBER_CANNOT_REMOVE_SELF = "member.cannot.remove.self";
    public static final String MEMBER_NOT_FOUND = "member.not.found";
    public static final String MEMBER_TARGET_NOT_FOUND = "member.transfer.target.not.found";
    public static final String MEMBER_TRANSFER_SELF = "member.transfer.self";

    // --- Invitations ---
    public static final String INVITATION_CANNOT_ASSIGN_OWNER = "invitation.cannot.assign.owner";
    public static final String INVITATION_ADMIN_CANNOT_INVITE_ADMIN = "invitation.admin.cannot.invite.admin";
    public static final String INVITATION_USER_ALREADY_MEMBER = "invitation.user.already.member";
    public static final String INVITATION_NOT_PENDING = "invitation.not.pending";
    public static final String INVITATION_NOT_FOUND = "invitation.not.found";
    public static final String INVITATION_ALREADY_ACCEPTED = "invitation.already.accepted";
    public static final String INVITATION_EXPIRED = "invitation.expired";
    public static final String INVITATION_ALREADY_MEMBER = "invitation.already.member";

    // --- Integration ---
    public static final String RATE_LIMITED = "api.rate.limited";
    public static final String CONNECTION_DUPLICATE = "connection.duplicate";
    public static final String CONNECTION_INVALID_STATE = "connection.invalid.state";
    public static final String CONNECTION_MARKETPLACE_MISMATCH = "connection.marketplace.mismatch";
    public static final String CREDENTIALS_INVALID = "credentials.invalid";
    public static final String VAULT_UNAVAILABLE = "vault.unavailable";
    public static final String INTEGRATION_YANDEX_BUSINESS_DISABLED =
            "integration.connection.yandex.business_disabled";
    public static final String INTEGRATION_YANDEX_TOKEN_INVALID =
            "integration.connection.yandex.token_invalid";
    /** Stored in {@code user_notification.title} / pushed over WS; translate on client for SYNC_COMPLETED. */
    public static final String INTEGRATION_NOTIFICATION_SYNC_COMPLETED_TITLE =
            "integration.notification.sync_completed.title";
    /** Stored in {@code user_notification.body}; translate on client for SYNC_COMPLETED. */
    public static final String INTEGRATION_NOTIFICATION_SYNC_COMPLETED_BODY =
            "integration.notification.sync_completed.body";

    // --- ETL ---
    public static final String JOB_NOT_FOUND = "job.not.found";
    public static final String JOB_NOT_RETRYABLE = "job.not.retryable";
    public static final String JOB_ACTIVE_EXISTS = "job.active.exists";
    public static final String COST_PROFILE_NOT_FOUND = "cost.profile.not_found";
    public static final String COST_PROFILE_INVALID = "cost.profile.invalid";
    public static final String COST_PROFILE_BULK_TOO_LARGE = "cost.profile.bulk.too.large";
    public static final String SELLER_SKU_NOT_FOUND = "seller.sku.not.found";

    // --- Audit & Alerting ---
    public static final String AUDIT_LOG_NOT_FOUND = "audit.log.not.found";
    public static final String ALERT_EVENT_NOT_FOUND = "alert.event.not.found";
    public static final String ALERT_EVENT_INVALID_STATE = "alert.event.invalid.state";
    public static final String ALERT_RULE_NOT_FOUND = "alert.rule.not.found";

    // --- Bidding: Policy ---
    public static final String BIDDING_POLICY_NOT_FOUND = "bidding.policy.not_found";
    public static final String BIDDING_POLICY_ALREADY_ACTIVE = "bidding.policy.already_active";
    public static final String BIDDING_POLICY_ALREADY_PAUSED = "bidding.policy.already_paused";
    public static final String BIDDING_POLICY_ARCHIVED = "bidding.policy.archived";
    public static final String BIDDING_ASSIGNMENT_CONFLICT = "bidding.assignment.conflict";
    public static final String BIDDING_ASSIGNMENT_NOT_FOUND = "bidding.assignment.not_found";
    public static final String BIDDING_RUN_NOT_FOUND = "bidding.run.not_found";
    public static final String BIDDING_RUN_COMPLETED = "bidding.run.completed";
    public static final String BIDDING_RUN_FAILED = "bidding.run.failed";
    public static final String BIDDING_RUN_PAUSED = "bidding.run.paused";

    // --- Bidding: Policy Config ---
    public static final String BIDDING_POLICY_CONFIG_INVALID = "bidding.policy.config_invalid";

    // --- Bidding: Action ---
    public static final String BIDDING_ACTION_NOT_FOUND = "bidding.action.not_found";
    public static final String BIDDING_ACTION_INVALID_STATE = "bidding.action.invalid_state";

    // --- Bidding: Guards ---
    public static final String BIDDING_GUARD_MANUAL_LOCK = "bidding.guard.manual_lock.blocked";
    public static final String BIDDING_GUARD_CAMPAIGN_INACTIVE = "bidding.guard.campaign_inactive.blocked";
    public static final String BIDDING_GUARD_STALE_DATA = "bidding.guard.stale_data.blocked";
    public static final String BIDDING_GUARD_STOCK_OUT = "bidding.guard.stock_out.blocked";
    public static final String BIDDING_GUARD_ECONOMY = "bidding.guard.economy.blocked";
    public static final String BIDDING_GUARD_LOW_STOCK = "bidding.guard.low_stock.blocked";
    public static final String BIDDING_GUARD_FREQUENCY = "bidding.guard.frequency.blocked";
    public static final String BIDDING_GUARD_DRR_CEILING = "bidding.guard.drr_ceiling.blocked";
    public static final String BIDDING_GUARD_DAILY_SPEND_LIMIT = "bidding.guard.daily_spend_limit.blocked";
    public static final String BIDDING_GUARD_VOLATILITY = "bidding.guard.volatility.blocked";
    public static final String BIDDING_GUARD_PRICE_COMPETITIVENESS = "bidding.guard.price_competitiveness.blocked";
    public static final String BIDDING_FULL_AUTO_INSUFFICIENT_RUNS = "bidding.full_auto.insufficient_runs";
    public static final String BIDDING_FULL_AUTO_HAS_FAILURES = "bidding.full_auto.has_failures";
    public static final String BIDDING_FULL_AUTO_HAS_FAILED_ACTIONS = "bidding.full_auto.has_failed_actions";
    public static final String BIDDING_ASSIGNMENT_CATEGORY_REQUIRED = "bidding.assignment.category_required";

    // --- Pricing: Guards ---
    public static final String PRICING_GUARD_MANUAL_LOCK = "pricing.guard.manual_lock";
    public static final String PRICING_GUARD_STALE_DATA_UNKNOWN = "pricing.guard.stale_data.unknown";
    public static final String PRICING_GUARD_STALE_DATA_STALE = "pricing.guard.stale_data.stale";
    public static final String PRICING_GUARD_STOCK_OUT = "pricing.guard.stock_out";
    public static final String PRICING_GUARD_MARGIN_NEGATIVE = "pricing.guard.margin_negative";
    public static final String PRICING_GUARD_MARGIN_BELOW_THRESHOLD = "pricing.guard.margin_below_threshold";
    public static final String PRICING_GUARD_FREQUENCY = "pricing.guard.frequency";
    public static final String PRICING_GUARD_VOLATILITY = "pricing.guard.volatility";
    public static final String PRICING_GUARD_PROMO_ACTIVE = "pricing.guard.promo_active";
    public static final String PRICING_GUARD_AD_COST_DRR_BLOCKED = "pricing.guard.ad_cost_drr.blocked";

    // --- Pricing: Strategy skip/hold reasons ---
    public static final String PRICING_COGS_MISSING = "pricing.strategy.cogs_missing";
    public static final String PRICING_COMMISSION_MISSING = "pricing.strategy.commission_missing";
    public static final String PRICING_DENOMINATOR_INVALID = "pricing.strategy.denominator_invalid";
    public static final String PRICING_CURRENT_PRICE_MISSING = "pricing.strategy.current_price_missing";
    public static final String PRICING_NO_CHANGE = "pricing.strategy.no_change";
    public static final String PRICING_VELOCITY_INSUFFICIENT_DATA = "pricing.velocity.insufficient_data";
    public static final String PRICING_VELOCITY_STABLE = "pricing.velocity.stable";
    public static final String PRICING_STOCK_NO_DATA = "pricing.stock.no_data";
    public static final String PRICING_STOCK_NORMAL = "pricing.stock.normal";
    public static final String PRICING_COMPOSITE_ALL_SKIPPED = "pricing.composite.all_skipped";
    public static final String PRICING_COMPETITOR_MISSING = "pricing.competitor.missing";
    public static final String PRICING_COMPETITOR_STALE = "pricing.competitor.stale";
    public static final String PRICING_COMPETITOR_UNTRUSTED = "pricing.competitor.untrusted";

    // --- Pricing: Policy API ---
    public static final String PRICING_POLICY_NOT_FOUND = "pricing.policy.not_found";
    public static final String PRICING_POLICY_ARCHIVED = "pricing.policy.archived";
    public static final String PRICING_POLICY_INVALID_STATE = "pricing.policy.invalid_state";
    public static final String PRICING_ASSIGNMENT_DUPLICATE = "pricing.assignment.duplicate";
    public static final String PRICING_LOCK_ALREADY_EXISTS = "pricing.lock.already_exists";
    public static final String PRICING_LOCK_NOT_FOUND = "pricing.lock.not_found";
    public static final String PRICING_RUN_ALREADY_IN_PROGRESS = "pricing.run.already_in_progress";
    public static final String PRICING_BULK_DUPLICATE = "pricing.bulk.duplicate";
    public static final String PRICING_BULK_TOO_LARGE = "pricing.bulk.too_large";
    public static final String PRICING_AUTOMATION_BLOCKED = "pricing.automation.blocked";
    public static final String PRICING_RUN_NOT_RESUMABLE = "pricing.run.not_resumable";
    public static final String PRICING_RUN_NOT_CANCELLABLE = "pricing.run.not_cancellable";
    public static final String PRICING_POLICY_FULL_AUTO_GATE_FAILED = "pricing.policy.full_auto_gate_failed";
    public static final String PRICING_POLICY_FULL_AUTO_CONFIRM_REQUIRED = "pricing.policy.full_auto_confirm_required";
    public static final String PRICING_POLICY_PREVIEW_REQUIRED = "pricing.policy.preview_required_for_full_auto";
    public static final String PRICING_RUN_BLAST_RADIUS_BREACHED = "pricing.run.blast_radius_breached";
    public static final String PRICING_RUN_NO_ACTIVE_ASSIGNMENTS = "pricing.run.no_active_assignments";

    // --- Pricing: Validation ---
    public static final String PRICING_TARGET_MARGIN_OUT_OF_RANGE =
            "pricing.validation.target_margin_out_of_range";

    // --- Pricing: Impact Preview ---
    public static final String PRICING_PREVIEW_OFFER_INACTIVE = "pricing.preview.offer_inactive";
    public static final String PRICING_PREVIEW_MANUAL_OVERRIDE = "pricing.preview.manual_override";

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
    public static final String PROMO_PRODUCT_ACTIVE_ACTION_EXISTS = "promo.product.active_action_exists";
    public static final String PROMO_CAMPAIGN_FROZEN = "promo.campaign.frozen";
    public static final String PROMO_WB_WRITE_UNAVAILABLE = "promo.wb.write_unavailable";

    // --- Mismatch Monitor ---
    public static final String MISMATCH_TIMELINE_DETECTED = "mismatches.timeline.detected";
    public static final String MISMATCH_TIMELINE_ACKNOWLEDGED = "mismatches.timeline.acknowledged";
    public static final String MISMATCH_TIMELINE_RESOLVED = "mismatches.timeline.resolved";
    public static final String MISMATCH_TIMELINE_IGNORED = "mismatches.timeline.ignored";
    public static final String MISMATCH_INVALID_RESOLUTION = "mismatches.invalid_resolution";
    public static final String MISMATCH_ESCALATE_MESSAGE = "mismatches.escalate.message";

    // --- Seller Operations ---
    public static final String GRID_EXPORT_TOO_MANY_ROWS = "grid.export.too_many_rows";
    public static final String SAVED_VIEW_NOT_FOUND = "saved_view.not_found";
    public static final String SAVED_VIEW_SYSTEM_READONLY = "saved_view.system_readonly";

    // --- Event-driven alert titles ---
    public static final String ALERT_ACTION_FAILED_TITLE = "alert.action_failed.title";
    public static final String ALERT_CONNECTION_HEALTH_DEGRADED_TITLE =
            "alert.connection_health_degraded.title";
    public static final String ALERT_PRICING_RUN_FAILED_TITLE = "alert.pricing_run_failed.title";
    public static final String ALERT_PROMO_EVALUATION_FAILED_TITLE =
            "alert.promo_evaluation_failed.title";

    // --- Notification titles ---
    public static final String NOTIFICATION_APPROVAL_REQUEST_TITLE =
            "notification.approval_request.title";
    public static final String NOTIFICATION_APPROVAL_REQUEST_BODY =
            "notification.approval_request.body";

    // --- Advertising Alerts ---
    public static final String AD_DRR_THRESHOLD_TITLE = "advertising.alert.drr_threshold.title";
    public static final String AD_NO_STOCK_TITLE = "advertising.alert.no_stock.title";
    public static final String AD_INEFFICIENT_CAMPAIGN_TITLE = "advertising.alert.inefficient_campaign.title";
    public static final String AD_PRICE_DROP_HIGH_DRR_TITLE = "advertising.alert.price_drop_high_drr.title";

    // --- Advertising Recommendations ---
    public static final String AD_RECOMMENDATION_WORTH = "advertising.recommendation.worth";
    public static final String AD_RECOMMENDATION_NOT_WORTH = "advertising.recommendation.not_worth";
    public static final String AD_RECOMMENDATION_REDUCE_BID = "advertising.recommendation.reduce_bid";
    public static final String AD_RECOMMENDATION_INSUFFICIENT_DATA = "advertising.recommendation.insufficient_data";

    // --- Autobidding Alerts ---
    public static final String AUTOBID_HIGH_DRR_CLUSTER_TITLE = "autobidding.alert.high_drr_cluster.title";
    public static final String AUTOBID_SPEND_SPIKE_TITLE = "autobidding.alert.spend_spike.title";
    public static final String AUTOBID_FULL_AUTO_ANOMALY_TITLE = "autobidding.alert.full_auto_anomaly.title";
    public static final String AUTOBID_NO_EFFECT_TITLE = "autobidding.alert.no_effect.title";
    public static final String AUTOBID_STRATEGY_EXHAUSTED_TITLE = "autobidding.alert.strategy_exhausted.title";

    // --- Pricing: AI Features ---
    public static final String PRICING_ADVISOR_UNAVAILABLE = "pricing.advisor.unavailable";
    public static final String PRICING_ADVISOR_GENERATION_FAILED = "pricing.advisor.generation_failed";
    public static final String PRICING_NARRATIVE_UNAVAILABLE = "pricing.narrative.unavailable";
    public static final String PRICING_INSIGHT_NOT_FOUND = "pricing.insight.not_found";
    public static final String PRICING_INSIGHT_ALREADY_ACKNOWLEDGED = "pricing.insight.already_acknowledged";

    // --- Analytics ---
    public static final String ANALYTICS_CLICKHOUSE_UNAVAILABLE = "analytics.clickhouse.unavailable";
    public static final String DATA_QUALITY_STALE_DOMAIN = "analytics.data_quality.block_reason.stale_domain";

    // --- Seller Operations: Working Queues ---
    public static final String QUEUE_NOT_FOUND = "queue.not_found";
    public static final String QUEUE_ITEM_NOT_FOUND = "queue.item.not_found";
    public static final String QUEUE_ITEM_INVALID_STATE = "queue.item.invalid_state";
    public static final String QUEUE_DUPLICATE = "queue.duplicate";
    public static final String QUEUE_SYSTEM_IMMUTABLE = "queue.system.immutable";
    public static final String QUEUE_ITEM_ALREADY_EXISTS = "queue.item.already_exists";
    public static final String QUEUE_ITEM_ALREADY_CLAIMED = "queues.item.already_claimed";
    public static final String QUEUE_LIMIT_EXCEEDED = "queue.limit.exceeded";
    public static final String SAVED_VIEW_LIMIT_EXCEEDED = "saved_view.limit.exceeded";

    // --- Common: Concurrent Modification ---
    public static final String CONCURRENT_MODIFICATION = "common.concurrent_modification";

    // --- ETL ---
    public static final String ETL_UNMAPPED_FINANCE_TYPES = "etl.alert.unmapped_finance_types";
}
