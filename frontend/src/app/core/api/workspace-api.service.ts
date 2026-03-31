import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { CreateTenantRequest, CreateWorkspaceRequest, TenantDetail, WorkspaceDetail } from '@core/models';

export interface WorkspaceSettingsResponse {
  id: number;
  name: string;
  slug: string;
  status: string;
  createdAt: string;
  tenantId: number;
  tenantName: string;
  tenantSlug: string;
}

@Injectable({ providedIn: 'root' })
export class WorkspaceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listWorkspaces(): Observable<WorkspaceDetail[]> {
    return this.http.get<WorkspaceDetail[]>(`${this.base}/workspaces`);
  }

  getWorkspace(workspaceId: number): Observable<WorkspaceSettingsResponse> {
    return this.http.get<WorkspaceSettingsResponse>(`${this.base}/workspaces/${workspaceId}`);
  }

  updateWorkspace(workspaceId: number, name: string): Observable<WorkspaceSettingsResponse> {
    return this.http.put<WorkspaceSettingsResponse>(`${this.base}/workspaces/${workspaceId}`, { name });
  }

  createTenant(req: CreateTenantRequest): Observable<TenantDetail> {
    return this.http.post<TenantDetail>(`${this.base}/tenants`, req);
  }

  createWorkspace(tenantId: number, req: CreateWorkspaceRequest): Observable<WorkspaceDetail> {
    return this.http.post<WorkspaceDetail>(`${this.base}/tenants/${tenantId}/workspaces`, req);
  }
}
