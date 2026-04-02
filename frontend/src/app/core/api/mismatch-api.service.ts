import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { Mismatch, MismatchDetail, MismatchFilter, MismatchSummary, Page } from '@core/models';

@Injectable({ providedIn: 'root' })
export class MismatchApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  getSummary(workspaceId: number): Observable<MismatchSummary> {
    return this.http.get<MismatchSummary>(`${this.base}/workspaces/${workspaceId}/mismatches/summary`);
  }

  list(workspaceId: number, filter: MismatchFilter, page: number, size: number, sort = 'detectedAt,desc'): Observable<Page<Mismatch>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.type?.length) params = params.set('type', filter.type.join(','));
    if (filter.connectionId?.length) params = params.set('connectionId', filter.connectionId.join(','));
    if (filter.status?.length) params = params.set('status', filter.status.join(','));
    if (filter.severity?.length) params = params.set('severity', filter.severity.join(','));
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);
    if (filter.query) params = params.set('query', filter.query);
    if (filter.offerId != null) params = params.set('offerId', filter.offerId);

    return this.http.get<Page<Mismatch>>(`${this.base}/workspaces/${workspaceId}/mismatches`, { params });
  }

  getDetail(workspaceId: number, mismatchId: number): Observable<MismatchDetail> {
    return this.http.get<MismatchDetail>(`${this.base}/workspaces/${workspaceId}/mismatches/${mismatchId}`);
  }

  acknowledge(workspaceId: number, mismatchId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/workspaces/${workspaceId}/mismatches/${mismatchId}/acknowledge`, null);
  }

  resolve(workspaceId: number, mismatchId: number, body: { resolution: string; note: string }): Observable<void> {
    return this.http.post<void>(`${this.base}/workspaces/${workspaceId}/mismatches/${mismatchId}/resolve`, body);
  }

  bulkIgnore(workspaceId: number, ids: number[], reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/workspaces/${workspaceId}/mismatches/bulk-ignore`, { ids, reason });
  }
}
