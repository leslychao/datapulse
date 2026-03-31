export type WorkspaceStatus = 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';

export interface WorkspaceDetail {
  id: number;
  name: string;
  slug: string;
  status: WorkspaceStatus;
  tenantId: number;
  tenantName: string;
  connectionsCount: number;
  membersCount: number;
}

export interface TenantDetail {
  id: number;
  name: string;
  slug: string;
}

export interface CreateTenantRequest {
  name: string;
}

export interface CreateWorkspaceRequest {
  name: string;
}
