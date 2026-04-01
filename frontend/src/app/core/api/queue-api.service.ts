import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { BulkActionResult, CreateQueueRequest, Page, Queue, QueueFilter, QueueItem } from '@core/models';

@Injectable({ providedIn: 'root' })
export class QueueApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listQueues(workspaceId: number): Observable<Queue[]> {
    return this.http.get<Queue[]>(`${this.base}/workspace/${workspaceId}/queues`);
  }

  getQueue(workspaceId: number, queueId: number): Observable<Queue> {
    return this.http.get<Queue>(`${this.base}/workspace/${workspaceId}/queues/${queueId}`);
  }

  createQueue(workspaceId: number, req: CreateQueueRequest): Observable<Queue> {
    return this.http.post<Queue>(`${this.base}/workspace/${workspaceId}/queues`, req);
  }

  updateQueue(workspaceId: number, queueId: number, req: CreateQueueRequest): Observable<Queue> {
    return this.http.put<Queue>(`${this.base}/workspace/${workspaceId}/queues/${queueId}`, req);
  }

  deleteQueue(workspaceId: number, queueId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/workspace/${workspaceId}/queues/${queueId}`);
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
    if (filter.connectionId?.length) params = params.set('connectionId', filter.connectionId.join(','));
    if (filter.query) params = params.set('query', filter.query);

    return this.http.get<Page<QueueItem>>(`${this.base}/workspace/${workspaceId}/queues/${queueId}/items`, { params });
  }

  claimItem(workspaceId: number, queueId: number, itemId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/workspace/${workspaceId}/queues/${queueId}/items/${itemId}/claim`, null);
  }

  completeItem(workspaceId: number, queueId: number, itemId: number, note?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/workspace/${workspaceId}/queues/${queueId}/items/${itemId}/done`, note ? { note } : null);
  }

  dismissItem(workspaceId: number, queueId: number, itemId: number, note?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/workspace/${workspaceId}/queues/${queueId}/items/${itemId}/dismiss`, note ? { note } : null);
  }

  previewCount(workspaceId: number, autoCriteria: CreateQueueRequest['autoCriteria']): Observable<{ matchCount: number }> {
    return this.http.post<{ matchCount: number }>(`${this.base}/workspace/${workspaceId}/queues/preview-count`, { autoCriteria });
  }

  bulkApprove(workspaceId: number, actionIds: number[]): Observable<BulkActionResult> {
    return this.http.post<BulkActionResult>(`${this.base}/workspace/${workspaceId}/actions/bulk-approve`, { actionIds });
  }

  bulkReject(workspaceId: number, actionIds: number[], cancelReason: string): Observable<BulkActionResult> {
    return this.http.post<BulkActionResult>(`${this.base}/workspace/${workspaceId}/actions/bulk-reject`, { actionIds, cancelReason });
  }
}
