import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { driver, type Driver } from 'driver.js';

import { TourDefinition } from '@core/models/tour.model';
import { TourProgressStore } from '@shared/stores/tour-progress.store';

const POLL_INTERVAL_MS = 200;
const MAX_WAIT_MS = 15_000;

@Injectable({ providedIn: 'root' })
export class GuidedTourService {
  private readonly translate = inject(TranslateService);
  private readonly tourProgress = inject(TourProgressStore);
  private readonly router = inject(Router);

  private driverInstance: Driver | null = null;
  private waitTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isRunning = signal(false);
  readonly currentTourId = signal<string | null>(null);

  startWhenReady(tour: TourDefinition): void {
    if (this.isRunning()) return;
    this.waitForElements(tour, () => this.start(tour));
  }

  start(tour: TourDefinition): void {
    if (this.isRunning()) return;

    this.driverInstance = driver({
      showProgress: true,
      animate: false,
      overlayColor: 'rgba(0, 0, 0, 0.55)',
      stagePadding: 8,
      stageRadius: 8,
      popoverClass: 'dp-tour-popover',
      nextBtnText: this.translate.instant('tour.next'),
      prevBtnText: this.translate.instant('tour.prev'),
      doneBtnText: this.translate.instant('tour.done'),
      onDestroyed: () => {
        this.isRunning.set(false);
        this.tourProgress.markCompleted(tour.id);
        this.currentTourId.set(null);
      },
      steps: tour.steps.map((step) => ({
        element: step.elementSelector,
        popover: {
          title: this.translate.instant(step.titleKey),
          description: this.translate.instant(step.descriptionKey),
          side: step.side ?? 'bottom',
        },
      })),
    });

    this.isRunning.set(true);
    this.currentTourId.set(tour.id);
    this.driverInstance.drive();
  }

  startWithNavigation(tour: TourDefinition): void {
    if (tour.requiredRoute && !this.router.url.includes(tour.requiredRoute)) {
      this.router.navigateByUrl(tour.requiredRoute).then((ok) => {
        if (ok) {
          this.waitForElements(tour, () => this.start(tour));
        }
      });
    } else {
      this.startWhenReady(tour);
    }
  }

  stop(): void {
    this.cancelWait();
    this.driverInstance?.destroy();
    this.driverInstance = null;
  }

  private waitForElements(tour: TourDefinition, onReady: () => void): void {
    this.cancelWait();
    const selectors = tour.steps.map((s) => s.elementSelector);
    const start = Date.now();

    const check = (): void => {
      const allPresent = selectors.every((sel) => document.querySelector(sel));
      if (allPresent || Date.now() - start > MAX_WAIT_MS) {
        this.waitTimer = null;
        onReady();
        return;
      }
      this.waitTimer = setTimeout(check, POLL_INTERVAL_MS);
    };

    this.waitTimer = setTimeout(check, POLL_INTERVAL_MS);
  }

  private cancelWait(): void {
    if (this.waitTimer) {
      clearTimeout(this.waitTimer);
      this.waitTimer = null;
    }
  }
}
