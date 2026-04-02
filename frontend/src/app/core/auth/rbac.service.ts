import { Injectable, inject, computed } from '@angular/core';

import { WorkspaceRole } from '@core/models';
import { AuthService } from './auth.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

const PRICING_WRITE_ROLES = new Set<WorkspaceRole>([
  'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const LOCK_MANAGE_ROLES = new Set<WorkspaceRole>([
  'OPERATOR', 'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const EXPORT_ROLES = new Set<WorkspaceRole>([
  'ANALYST', 'OPERATOR', 'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const ACTION_APPROVE_ROLES = new Set<WorkspaceRole>([
  'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const ACTION_OPERATE_ROLES = new Set<WorkspaceRole>([
  'OPERATOR', 'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const ACTION_RECONCILE_ROLES = new Set<WorkspaceRole>([
  'ADMIN', 'OWNER',
]);

const SIMULATION_RESET_ROLES = new Set<WorkspaceRole>([
  'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const PROMO_WRITE_ROLES = new Set<WorkspaceRole>([
  'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const PROMO_APPROVE_ROLES = new Set<WorkspaceRole>([
  'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const PROMO_OPERATE_ROLES = new Set<WorkspaceRole>([
  'OPERATOR', 'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const MISMATCH_OPERATE_ROLES = new Set<WorkspaceRole>([
  'OPERATOR', 'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const MISMATCH_IGNORE_ROLES = new Set<WorkspaceRole>([
  'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const SETTINGS_ADMIN_ROLES = new Set<WorkspaceRole>([
  'ADMIN', 'OWNER',
]);

const COST_EDIT_ROLES = new Set<WorkspaceRole>([
  'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

const ALERT_VIEW_ROLES = new Set<WorkspaceRole>([
  'OPERATOR', 'PRICING_MANAGER', 'ADMIN', 'OWNER',
]);

@Injectable({ providedIn: 'root' })
export class RbacService {
  private readonly auth = inject(AuthService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly currentRole = computed<WorkspaceRole | null>(() => {
    const user = this.auth.user();
    const wsId = this.wsStore.currentWorkspaceId();
    if (!user || wsId == null) return null;
    const membership = user.memberships.find((m) => m.workspaceId === wsId);
    return membership?.role ?? null;
  });

  readonly canWritePolicies = computed(
    () => hasRole(this.currentRole(), PRICING_WRITE_ROLES),
  );

  readonly canManageLocks = computed(
    () => hasRole(this.currentRole(), LOCK_MANAGE_ROLES),
  );

  readonly canExport = computed(
    () => hasRole(this.currentRole(), EXPORT_ROLES),
  );

  readonly canApproveActions = computed(
    () => hasRole(this.currentRole(), ACTION_APPROVE_ROLES),
  );

  readonly canOperateActions = computed(
    () => hasRole(this.currentRole(), ACTION_OPERATE_ROLES),
  );

  readonly canReconcileActions = computed(
    () => hasRole(this.currentRole(), ACTION_RECONCILE_ROLES),
  );

  readonly canResetSimulation = computed(
    () => hasRole(this.currentRole(), SIMULATION_RESET_ROLES),
  );

  readonly canWritePromo = computed(
    () => hasRole(this.currentRole(), PROMO_WRITE_ROLES),
  );

  readonly canApprovePromo = computed(
    () => hasRole(this.currentRole(), PROMO_APPROVE_ROLES),
  );

  readonly canOperatePromo = computed(
    () => hasRole(this.currentRole(), PROMO_OPERATE_ROLES),
  );

  readonly canOperateMismatches = computed(
    () => hasRole(this.currentRole(), MISMATCH_OPERATE_ROLES),
  );

  readonly canIgnoreMismatches = computed(
    () => hasRole(this.currentRole(), MISMATCH_IGNORE_ROLES),
  );

  readonly isAdmin = computed(
    () => hasRole(this.currentRole(), SETTINGS_ADMIN_ROLES),
  );

  readonly isOwner = computed(
    () => this.currentRole() === 'OWNER',
  );

  readonly canEditCostProfiles = computed(
    () => hasRole(this.currentRole(), COST_EDIT_ROLES),
  );

  readonly canViewAlertRules = computed(
    () => hasRole(this.currentRole(), ALERT_VIEW_ROLES),
  );

  readonly currentUserId = computed(() => this.auth.user()?.id ?? null);
}

function hasRole(
  role: WorkspaceRole | null,
  allowed: Set<WorkspaceRole>,
): boolean {
  return role != null && allowed.has(role);
}
