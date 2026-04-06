import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { LucideAngularModule, HelpCircle, Check, RotateCcw } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';

import { TourGroup } from '@core/models/tour.model';
import { GuidedTourService } from '@shared/services/guided-tour.service';
import { TourProgressStore } from '@shared/stores/tour-progress.store';
import { TOUR_REGISTRY } from './tour-registry';

@Component({
  selector: 'dp-tour-catalog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule, TranslatePipe],
  template: `
    <div class="relative">
      <button
        (click)="toggle()"
        class="flex cursor-pointer items-center justify-center rounded-[var(--radius-md)] p-1.5
               text-[var(--text-secondary)] transition-colors
               hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        aria-label="Обучение"
      >
        <lucide-icon [img]="HelpCircleIcon" [size]="18" />
      </button>

      @if (open()) {
        <div
          class="absolute right-0 top-full z-50 mt-1 flex w-[300px] flex-col overflow-hidden
                 rounded-[var(--radius-lg)] border border-[var(--border-default)]
                 bg-[var(--bg-primary)] shadow-[var(--shadow-lg)]"
        >
          <div class="flex items-center justify-between border-b border-[var(--border-default)] px-3 py-2.5">
            <span class="text-[length:var(--text-sm)] font-semibold text-[var(--text-primary)]">
              {{ 'tour.catalog.title' | translate }}
            </span>
            <button
              (click)="resetProgress()"
              class="flex cursor-pointer items-center gap-1 text-[length:var(--text-xs)]
                     text-[var(--text-tertiary)] transition-colors hover:text-[var(--text-primary)]"
              [title]="'tour.catalog.reset' | translate"
            >
              <lucide-icon [img]="RotateCcwIcon" [size]="12" />
            </button>
          </div>

          <div class="max-h-[400px] overflow-y-auto py-1">
            @for (group of registry; track group.titleKey) {
              <div class="px-3 pb-1 pt-2.5">
                <span class="text-[length:var(--text-xs)] font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">
                  {{ group.titleKey | translate }}
                </span>
              </div>
              @for (tour of group.tours; track tour.id) {
                <button
                  (click)="startTour(tour.id)"
                  class="flex w-full cursor-pointer items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-[var(--bg-tertiary)]"
                >
                  @if (tourProgress.isCompleted(tour.id)) {
                    <lucide-icon
                      [img]="CheckIcon"
                      [size]="14"
                      class="shrink-0 text-[var(--status-success)]"
                    />
                  } @else {
                    <span class="inline-block h-3.5 w-3.5 shrink-0"></span>
                  }
                  <span class="text-[length:var(--text-sm)] text-[var(--text-primary)]">
                    {{ tour.titleKey | translate }}
                  </span>
                </button>
              }
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class TourCatalogComponent {
  protected readonly HelpCircleIcon = HelpCircle;
  protected readonly CheckIcon = Check;
  protected readonly RotateCcwIcon = RotateCcw;

  protected readonly tourService = inject(GuidedTourService);
  protected readonly tourProgress = inject(TourProgressStore);
  private readonly elementRef = inject(ElementRef);

  protected readonly registry: TourGroup[] = TOUR_REGISTRY;
  protected readonly open = signal(false);

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.open.set(false);
    }
  }

  protected toggle(): void {
    this.open.update((v) => !v);
  }

  protected startTour(tourId: string): void {
    this.open.set(false);
    for (const group of this.registry) {
      const tour = group.tours.find((t) => t.id === tourId);
      if (tour) {
        this.tourService.startWithNavigation(tour);
        return;
      }
    }
  }

  protected resetProgress(): void {
    this.tourProgress.resetAll();
  }
}
