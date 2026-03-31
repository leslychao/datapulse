export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'PRICING_MANAGER' | 'OPERATOR' | 'ANALYST' | 'VIEWER';

export interface WorkspaceMembership {
  workspaceId: number;
  workspaceName: string;
  tenantId: number;
  tenantName: string;
  role: WorkspaceRole;
}

export interface UserProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  needsOnboarding: boolean;
  memberships: WorkspaceMembership[];
}
