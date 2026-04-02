import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AnalyticsHealthService {
  readonly clickhouseUnavailable = signal(false);

  markUnavailable(): void {
    this.clickhouseUnavailable.set(true);
  }

  markAvailable(): void {
    this.clickhouseUnavailable.set(false);
  }
}
