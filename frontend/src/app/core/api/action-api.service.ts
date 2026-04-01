import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  ActionDetail,
  ActionFilter,
  ActionSummary,
  BulkApproveRequest,
  BulkApproveResponse,
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
      `${this.base}/workspace/${workspaceId}/actions`,
      { params },
    );
  }

  getAction(workspaceId: number, actionId: number): Observable<ActionDetail> {
    return this.http.get<ActionDetail>(
      `${this.base}/workspace/${workspaceId}/actions/${actionId}`,
    );
  }

  approveAction(actionId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/actions/${actionId}/approve`, {});
  }

  rejectAction(actionId: number, cancelReason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/actions/${actionId}/reject`, { cancelReason });
  }

  holdAction(actionId: number, holdReason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/actions/${actionId}/hold`, { holdReason });
  }

  resumeAction(actionId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/actions/${actionId}/resume`, {});
  }

  cancelAction(actionId: number, cancelReason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/actions/${actionId}/cancel`, { cancelReason });
  }

  retryAction(actionId: number, retryReason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/actions/${actionId}/retry`, { retryReason });
  }

  bulkApprove(req: BulkApproveRequest): Observable<BulkApproveResponse> {
    return this.http.post<BulkApproveResponse>(`${this.base}/actions/bulk-approve`, req);
  }
}
