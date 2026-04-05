import { inject, Injectable, signal } from '@angular/core';

import { AnalyticsApiService } from '@core/api/analytics-api.service';

@Injectable({ providedIn: 'root' })
export class AnalyticsHealthService {
  private readonly analyticsApi = inject(AnalyticsApiService);
  readonly clickhouseUnavailable = signal(false);

  checkHealth(workspaceId: number): void {
    this.analyticsApi.getChHealth(workspaceId).subscribe({
      next: (r) => this.clickhouseUnavailable.set(!r.available),
      error: () => this.clickhouseUnavailable.set(true),
    });
  }

  markUnavailable(): void {
    this.clickhouseUnavailable.set(true);
  }

  markAvailable(): void {
    this.clickhouseUnavailable.set(false);
  }
}
