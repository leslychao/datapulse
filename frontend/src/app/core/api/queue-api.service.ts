import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import {
  CreateQueueRequest,
  Page,
  Queue,
  QueueFilter,
  QueueItem,
  UpdateQueueRequest,
} from '@core/models';

@Injectable({ providedIn: 'root' })
export class QueueApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listQueues(workspaceId: number): Observable<Queue[]> {
    return this.http.get<Queue[]>(`${this.base}/workspaces/${workspaceId}/queues`);
  }

  getQueue(workspaceId: number, queueId: number): Observable<Queue> {
    return this.http.get<Queue>(`${this.base}/workspaces/${workspaceId}/queues/${queueId}`);
  }

  createQueue(workspaceId: number, req: CreateQueueRequest): Observable<Queue> {
    return this.http.post<Queue>(`${this.base}/workspaces/${workspaceId}/queues`, req);
  }

  updateQueue(workspaceId: number, queueId: number, req: UpdateQueueRequest): Observable<Queue> {
    return this.http.put<Queue>(`${this.base}/workspaces/${workspaceId}/queues/${queueId}`, req);
  }

  deleteQueue(workspaceId: number, queueId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/workspaces/${workspaceId}/queues/${queueId}`);
  }

  listItems(workspaceId: number, queueId: number, filter: QueueFilter, page: number, size: number, sort = 'created_at', direction = 'ASC'): Observable<Page<QueueItem>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort)
      .set('direction', direction);

    if (filter.status?.length) params = params.set('status', filter.status.join(','));
    if (filter.assignedToMe) params = params.set('assignedToMe', true);
    if (filter.marketplaceType?.length) params = params.set('marketplaceType', filter.marketplaceType.join(','));
    if (filter.query) params = params.set('query', filter.query);
    if (filter.severity?.length) params = params.set('severity', filter.severity.join(','));
    if (filter.mismatchType?.length) params = params.set('mismatchType', filter.mismatchType.join(','));
    if (filter.decisionType?.length) params = params.set('decisionType', filter.decisionType.join(','));
    if (filter.actionStatus?.length) params = params.set('actionStatus', filter.actionStatus.join(','));

    return this.http.get<Page<QueueItem>>(`${this.base}/workspaces/${workspaceId}/queues/${queueId}/items`, { params });
  }

  claimItem(workspaceId: number, queueId: number, itemId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/workspaces/${workspaceId}/queues/${queueId}/items/${itemId}/claim`, null);
  }

  completeItem(workspaceId: number, queueId: number, itemId: number, note?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/workspaces/${workspaceId}/queues/${queueId}/items/${itemId}/done`, note ? { note } : null);
  }

  dismissItem(workspaceId: number, queueId: number, itemId: number, note?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/workspaces/${workspaceId}/queues/${queueId}/items/${itemId}/dismiss`, note ? { note } : null);
  }

  previewCount(workspaceId: number, autoCriteria: CreateQueueRequest['autoCriteria']): Observable<{ matchCount: number }> {
    return this.http.post<{ matchCount: number }>(`${this.base}/workspaces/${workspaceId}/queues/preview-count`, { autoCriteria });
  }
}
