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
}

function hasRole(
  role: WorkspaceRole | null,
  allowed: Set<WorkspaceRole>,
): boolean {
  return role != null && allowed.has(role);
}
