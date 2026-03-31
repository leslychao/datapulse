import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';

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
}
