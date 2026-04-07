import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { driver, type Driver } from 'driver.js';

import { TourDefinition } from '@core/models/tour.model';
import { TourProgressStore } from '@shared/stores/tour-progress.store';

@Injectable({ providedIn: 'root' })
export class GuidedTourService {
  private readonly translate = inject(TranslateService);
  private readonly tourProgress = inject(TourProgressStore);
  private readonly router = inject(Router);

  private driverInstance: Driver | null = null;

  readonly isRunning = signal(false);
  readonly currentTourId = signal<string | null>(null);

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
          setTimeout(() => this.start(tour), 800);
        }
      });
    } else {
      this.start(tour);
    }
  }

  stop(): void {
    this.driverInstance?.destroy();
    this.driverInstance = null;
  }
}
