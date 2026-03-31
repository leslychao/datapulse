import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { CreateInvitationRequest, Invitation } from '@core/models';

export interface AcceptInvitationResponse {
  workspaceId: number;
  workspaceName: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class InvitationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  acceptInvitation(token: string): Observable<AcceptInvitationResponse> {
    return this.http.post<AcceptInvitationResponse>(`${this.base}/invitations/accept`, { token });
  }

  listInvitations(workspaceId: number): Observable<Invitation[]> {
    return this.http.get<Invitation[]>(`${this.base}/workspaces/${workspaceId}/invitations`);
  }

  createInvitation(workspaceId: number, req: CreateInvitationRequest): Observable<Invitation> {
    return this.http.post<Invitation>(`${this.base}/workspaces/${workspaceId}/invitations`, req);
  }

  cancelInvitation(workspaceId: number, invitationId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/workspaces/${workspaceId}/invitations/${invitationId}`);
  }

  resendInvitation(workspaceId: number, invitationId: number): Observable<Invitation> {
    return this.http.post<Invitation>(
      `${this.base}/workspaces/${workspaceId}/invitations/${invitationId}/resend`, {},
    );
  }
}
