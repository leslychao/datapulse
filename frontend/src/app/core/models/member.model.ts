import { WorkspaceRole } from './user.model';

export type MemberStatus = 'ACTIVE' | 'INACTIVE';

export interface Member {
  userId: number;
  email: string;
  name: string;
  role: WorkspaceRole;
  status: MemberStatus;
  createdAt: string;
}

export interface UpdateMemberRoleRequest {
  role: WorkspaceRole;
}
