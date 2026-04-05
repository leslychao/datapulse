import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { CampaignDashboardFilter, CampaignSummary, Page } from '@core/models';

@Injectable({ providedIn: 'root' })
export class AdvertisingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listCampaigns(
    workspaceId: number,
    filter: CampaignDashboardFilter,
    page: number,
    size: number,
    sort = 'name,asc',
  ): Observable<Page<CampaignSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.period) {
      params = params.set('period', filter.period);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.connectionIds?.length) {
      for (const id of filter.connectionIds) {
        params = params.append('connectionIds', id);
      }
    }

    return this.http.get<Page<CampaignSummary>>(
      `${this.base}/workspaces/${workspaceId}/advertising/campaigns`,
      { params },
    );
  }
}
