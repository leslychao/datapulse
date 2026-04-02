import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  CostProfile,
  CostProfileImportResult,
  CostProfilePage,
  CreateCostProfileRequest,
  UpdateCostProfileRequest,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class CostProfileApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listCostProfiles(search?: string, page = 0, size = 50): Observable<CostProfilePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<CostProfilePage>(`${this.base}/cost-profiles`, { params });
  }

  createCostProfile(req: CreateCostProfileRequest): Observable<CostProfile> {
    return this.http.post<CostProfile>(`${this.base}/cost-profiles`, req);
  }

  updateCostProfile(id: number, req: UpdateCostProfileRequest): Observable<CostProfile> {
    return this.http.put<CostProfile>(`${this.base}/cost-profiles/${id}`, req);
  }

  importCsv(file: File): Observable<CostProfileImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CostProfileImportResult>(
      `${this.base}/cost-profiles/bulk-import`,
      formData,
    );
  }

  exportCsv(): Observable<Blob> {
    return this.http.get(`${this.base}/cost-profiles/export`, {
      responseType: 'blob',
    });
  }
}
