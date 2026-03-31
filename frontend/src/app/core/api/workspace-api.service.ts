import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { CreateTenantRequest, CreateWorkspaceRequest, TenantDetail, WorkspaceDetail } from '@core/models';

@Injectable({ providedIn: 'root' })
export class WorkspaceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listWorkspaces(): Observable<WorkspaceDetail[]> {
    return this.http.get<WorkspaceDetail[]>(`${this.base}/workspaces`);
  }

  createTenant(req: CreateTenantRequest): Observable<TenantDetail> {
    return this.http.post<TenantDetail>(`${this.base}/tenants`, req);
  }

  createWorkspace(tenantId: number, req: CreateWorkspaceRequest): Observable<WorkspaceDetail> {
    return this.http.post<WorkspaceDetail>(`${this.base}/tenants/${tenantId}/workspaces`, req);
  }
}
