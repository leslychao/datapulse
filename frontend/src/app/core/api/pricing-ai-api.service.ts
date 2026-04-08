import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { PricingInsight, Page } from '@core/models';

@Injectable({ providedIn: 'root' })
export class PricingAiApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listInsights(
    workspaceId: number,
    type?: string,
    acknowledged?: boolean,
    page = 0,
    size = 20,
  ): Observable<Page<PricingInsight>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'createdAt,desc');
    if (type) {
      params = params.set('type', type);
    }
    if (acknowledged !== undefined) {
      params = params.set('acknowledged', acknowledged);
    }
    return this.http.get<Page<PricingInsight>>(
      `${this.base}/workspaces/${workspaceId}/pricing/insights`,
      { params },
    );
  }

  countUnacknowledged(workspaceId: number): Observable<number> {
    return this.http.get<number>(
      `${this.base}/workspaces/${workspaceId}/pricing/insights/count`,
    );
  }

  acknowledgeInsight(
    workspaceId: number,
    insightId: number,
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/workspaces/${workspaceId}/pricing/insights/${insightId}/acknowledge`,
      {},
    );
  }
}
