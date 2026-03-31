import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { Member, UpdateMemberRoleRequest } from '@core/models';

@Injectable({ providedIn: 'root' })
export class MemberApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listMembers(workspaceId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.base}/workspaces/${workspaceId}/members`);
  }

  changeRole(workspaceId: number, userId: number, req: UpdateMemberRoleRequest): Observable<Member> {
    return this.http.put<Member>(`${this.base}/workspaces/${workspaceId}/members/${userId}/role`, req);
  }

  removeMember(workspaceId: number, userId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/workspaces/${workspaceId}/members/${userId}`);
  }
}
