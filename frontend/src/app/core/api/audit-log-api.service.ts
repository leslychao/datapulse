import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { AuditLogFilter, AuditLogPage } from '@core/models';

@Injectable({ providedIn: 'root' })
export class AuditLogApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listAuditLog(filter: AuditLogFilter = {}): Observable<AuditLogPage> {
    let params = new HttpParams();
    if (filter.userId) params = params.set('userId', filter.userId);
    if (filter.actionType) params = params.set('actionType', filter.actionType);
    if (filter.entityType) params = params.set('entityType', filter.entityType);
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);
    params = params.set('page', filter.page ?? 0);
    params = params.set('size', filter.size ?? 50);

    return this.http.get<AuditLogPage>(`${this.base}/audit-log`, { params });
  }
}
