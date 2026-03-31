import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { AppNotification, UnreadCount } from '@core/models';

@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  list(params: { size?: number; since?: string }): Observable<AppNotification[]> {
    let httpParams = new HttpParams();
    if (params.size != null) {
      httpParams = httpParams.set('size', params.size);
    }
    if (params.since != null) {
      httpParams = httpParams.set('since', params.since);
    }
    return this.http.get<AppNotification[]>(`${this.base}/notifications`, { params: httpParams });
  }

  getUnreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(`${this.base}/notifications/unread-count`);
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/notifications/${id}/read`, null);
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/notifications/read-all`, null);
  }
}
