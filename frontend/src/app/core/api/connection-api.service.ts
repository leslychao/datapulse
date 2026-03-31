import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { ConnectionDetail, CreateConnectionRequest } from '@core/models';

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

  listConnections(): Observable<ConnectionDetail[]> {
    return this.http.get<ConnectionDetail[]>(`${this.base}/connections`);
  }
}
