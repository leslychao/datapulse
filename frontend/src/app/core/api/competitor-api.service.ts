import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  BulkCompetitorUploadResponse,
  CompetitorMatch,
  CompetitorObservation,
  CreateCompetitorMatchRequest,
  CreateCompetitorObservationRequest,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class CompetitorApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listMatches(
    workspaceId: number,
    marketplaceOfferId?: number,
  ): Observable<CompetitorMatch[]> {
    let params = new HttpParams();
    if (marketplaceOfferId != null) {
      params = params.set('marketplaceOfferId', marketplaceOfferId);
    }
    return this.http.get<CompetitorMatch[]>(
      `${this.base}/workspaces/${workspaceId}/competitors/matches`,
      { params },
    );
  }

  createMatch(
    workspaceId: number,
    req: CreateCompetitorMatchRequest,
  ): Observable<CompetitorMatch> {
    return this.http.post<CompetitorMatch>(
      `${this.base}/workspaces/${workspaceId}/competitors/matches`,
      req,
    );
  }

  deleteMatch(workspaceId: number, matchId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/workspaces/${workspaceId}/competitors/matches/${matchId}`,
    );
  }

  addObservation(
    workspaceId: number,
    matchId: number,
    req: CreateCompetitorObservationRequest,
  ): Observable<CompetitorObservation> {
    return this.http.post<CompetitorObservation>(
      `${this.base}/workspaces/${workspaceId}/competitors/matches/${matchId}/observations`,
      req,
    );
  }

  listObservations(
    workspaceId: number,
    matchId: number,
  ): Observable<CompetitorObservation[]> {
    return this.http.get<CompetitorObservation[]>(
      `${this.base}/workspaces/${workspaceId}/competitors/matches/${matchId}/observations`,
    );
  }

  bulkUpload(
    workspaceId: number,
    file: File,
  ): Observable<BulkCompetitorUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<BulkCompetitorUploadResponse>(
      `${this.base}/workspaces/${workspaceId}/competitors/bulk-upload`,
      formData,
    );
  }
}
