import { WorkspaceRole } from './user.model';

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'CANCELLED' | 'EXPIRED';

export interface Invitation {
  id: number;
  email: string;
  role: WorkspaceRole;
  status: InvitationStatus;
  createdAt: string;
  expiresAt: string;
}

export interface CreateInvitationRequest {
  email: string;
  role: WorkspaceRole;
}

export interface AcceptInvitationResponse {
  workspaceId: number;
  workspaceName: string;
  role: string;
}
