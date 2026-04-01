import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { AlertRule, UpdateAlertRuleRequest } from '@core/models';

@Injectable({ providedIn: 'root' })
export class AlertRuleApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listAlertRules(): Observable<AlertRule[]> {
    return this.http.get<AlertRule[]>(`${this.base}/alert-rules`);
  }

  updateAlertRule(id: number, req: UpdateAlertRuleRequest): Observable<AlertRule> {
    return this.http.put<AlertRule>(`${this.base}/alert-rules/${id}`, req);
  }

  toggleEnabled(id: number, enabled: boolean): Observable<AlertRule> {
    return this.http.put<AlertRule>(`${this.base}/alert-rules/${id}`, { enabled });
  }
}
