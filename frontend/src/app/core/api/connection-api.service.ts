import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  CallLogEntry,
  CallLogFilter,
  ConnectionDetail,
  ConnectionSummary,
  CreateConnectionRequest,
  Page,
  SyncState,
  UpdateCredentialsRequest,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class ConnectionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  createConnection(req: CreateConnectionRequest): Observable<ConnectionDetail> {
    return this.http.post<ConnectionDetail>(`${this.base}/connections`, req);
  }

  getConnection(id: number): Observable<ConnectionDetail> {
    return this.http.get<ConnectionDetail>(`${this.base}/connections/${id}`);
  }

  listConnections(): Observable<ConnectionSummary[]> {
    return this.http.get<ConnectionSummary[]>(`${this.base}/connections`);
  }

  updateCredentials(id: number, req: UpdateCredentialsRequest): Observable<ConnectionDetail> {
    return this.http.put<ConnectionDetail>(`${this.base}/connections/${id}/credentials`, req);
  }

  validateConnection(id: number): Observable<{ valid: boolean; errorCode?: string }> {
    return this.http.post<{ valid: boolean; errorCode?: string }>(
      `${this.base}/connections/${id}/validate`, {},
    );
  }

  disableConnection(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/connections/${id}/disable`, {});
  }

  enableConnection(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/connections/${id}/enable`, {});
  }

  archiveConnection(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/connections/${id}`);
  }

  getSyncStates(connectionId: number): Observable<SyncState[]> {
    return this.http.get<SyncState[]>(`${this.base}/connections/${connectionId}/sync-state`);
  }

  triggerSync(connectionId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/connections/${connectionId}/sync`, {});
  }

  getCallLog(connectionId: number, filter: CallLogFilter, page: number, size: number): Observable<Page<CallLogEntry>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'createdAt,desc');
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);
    if (filter.endpoint) params = params.set('endpoint', filter.endpoint);
    if (filter.httpStatus) params = params.set('httpStatus', filter.httpStatus);
    return this.http.get<Page<CallLogEntry>>(`${this.base}/connections/${connectionId}/call-log`, { params });
  }
}
