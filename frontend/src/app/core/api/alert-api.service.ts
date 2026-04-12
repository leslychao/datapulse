import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { AlertEvent, AlertFilter, AlertSummary, Page } from '@core/models';

@Injectable({ providedIn: 'root' })
export class AlertApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listAlerts(filter: AlertFilter, page: number, size: number, sort = 'openedAt,desc'): Observable<Page<AlertEvent>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.ruleType?.length) params = params.set('ruleType', filter.ruleType.join(','));
    if (filter.severity?.length) params = params.set('severity', filter.severity.join(','));
    if (filter.connectionId != null) params = params.set('connectionId', filter.connectionId);
    if (filter.status?.length) params = params.set('status', filter.status.join(','));
    if (filter.blocksAutomation != null) params = params.set('blocksAutomation', filter.blocksAutomation);

    return this.http.get<Page<AlertEvent>>(`${this.base}/alerts`, { params });
  }

  getAlert(id: number): Observable<AlertEvent> {
    return this.http.get<AlertEvent>(`${this.base}/alerts/${id}`);
  }

  getSummary(): Observable<AlertSummary> {
    return this.http.get<AlertSummary>(`${this.base}/alerts/summary`);
  }

  acknowledge(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/alerts/${id}/acknowledge`, null);
  }

  resolve(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/alerts/${id}/resolve`, null);
  }

}
