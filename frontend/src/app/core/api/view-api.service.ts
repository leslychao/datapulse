import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { CreateViewRequest, GridView, UpdateViewRequest } from '@core/models';

@Injectable({ providedIn: 'root' })
export class ViewApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listViews(workspaceId: number): Observable<GridView[]> {
    return this.http.get<GridView[]>(
      `${this.base}/workspace/${workspaceId}/views`,
    );
  }

  createView(workspaceId: number, req: CreateViewRequest): Observable<GridView> {
    return this.http.post<GridView>(
      `${this.base}/workspace/${workspaceId}/views`,
      req,
    );
  }

  updateView(workspaceId: number, viewId: number, req: UpdateViewRequest): Observable<GridView> {
    return this.http.put<GridView>(
      `${this.base}/workspace/${workspaceId}/views/${viewId}`,
      req,
    );
  }

  deleteView(workspaceId: number, viewId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspace/${workspaceId}/views/${viewId}`,
    );
  }
}
