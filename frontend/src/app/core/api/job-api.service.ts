import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env';
import { JobFilter, JobItem, JobRetryResult, JobSummary, Page } from '@core/models';

@Injectable({ providedIn: 'root' })
export class JobApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listJobs(
      connectionId: number,
      filter: JobFilter,
      page: number,
      size: number,
      sort: string = 'createdAt,desc'): Observable<Page<JobSummary>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    if (filter.status) params = params.set('status', filter.status);
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);

    return this.http.get<Page<JobSummary>>(
      `${this.base}/connections/${connectionId}/jobs`,
      { params },
    );
  }

  getJob(jobId: number): Observable<JobSummary> {
    return this.http.get<JobSummary>(`${this.base}/jobs/${jobId}`);
  }

  listJobItems(jobId: number): Observable<JobItem[]> {
    return this.http.get<JobItem[]>(`${this.base}/jobs/${jobId}/items`);
  }

  retryJob(jobId: number): Observable<JobRetryResult> {
    return this.http.post<JobRetryResult>(`${this.base}/jobs/${jobId}/retry`, {});
  }
}
