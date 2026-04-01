import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  ActionDetail,
  ActionFilter,
  ActionSummary,
  BulkActionResponse,
  BulkApproveRequest,
  Page,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class ActionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listActions(
    workspaceId: number,
    filter: ActionFilter,
    page: number,
    size: number,
    sort = 'createdAt,desc',
  ): Observable<Page<ActionSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.connectionId) {
      params = params.set('connectionId', filter.connectionId);
    }
    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.executionMode) {
      params = params.set('executionMode', filter.executionMode);
    }
    if (filter.search) {
      params = params.set('search', filter.search);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }

    return this.http.get<Page<ActionSummary>>(
      `${this.base}/workspaces/${workspaceId}/actions`,
      { params },
    );
  }

  getAction(workspaceId: number, actionId: number): Observable<ActionDetail> {
    return this.http.get<ActionDetail>(
      `${this.base}/workspaces/${workspaceId}/actions/${actionId}`,
    );
  }

  approveAction(workspaceId: number, actionId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/actions/${actionId}/approve`, {},
    );
  }

  rejectAction(workspaceId: number, actionId: number, cancelReason: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/actions/${actionId}/reject`, { cancelReason },
    );
  }

  holdAction(workspaceId: number, actionId: number, holdReason: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/actions/${actionId}/hold`, { holdReason },
    );
  }

  resumeAction(workspaceId: number, actionId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/actions/${actionId}/resume`, {},
    );
  }

  cancelAction(workspaceId: number, actionId: number, cancelReason: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/actions/${actionId}/cancel`, { cancelReason },
    );
  }

  retryAction(workspaceId: number, actionId: number, retryReason: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/actions/${actionId}/retry`, { retryReason },
    );
  }

  bulkApprove(workspaceId: number, req: BulkApproveRequest): Observable<BulkActionResponse> {
    return this.http.post<BulkActionResponse>(
      `${this.base}/workspaces/${workspaceId}/actions/bulk-approve`, req,
    );
  }

  getSimulationComparison(workspaceId: number, connectionId: number): Observable<any> {
    const params = new HttpParams().set('connectionId', connectionId);
    return this.http.get(
      `${this.base}/workspaces/${workspaceId}/simulation/comparison`,
      { params },
    );
  }

  resetShadowState(workspaceId: number, connectionId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspaces/${workspaceId}/simulation/shadow-state`,
      { body: { connectionId } },
    );
  }
}
