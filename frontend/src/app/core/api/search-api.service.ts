import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { SearchResult } from '@core/models';

@Injectable({ providedIn: 'root' })
export class SearchApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  search(workspaceId: number, query: string, limit?: number): Observable<SearchResult> {
    let params = new HttpParams().set('q', query);
    if (limit != null) {
      params = params.set('limit', limit);
    }
    return this.http.get<SearchResult>(`${this.base}/workspaces/${workspaceId}/search`, { params });
  }
}
