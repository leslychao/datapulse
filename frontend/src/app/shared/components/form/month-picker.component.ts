import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { LucideAngularModule, Calendar, ChevronLeft, ChevronRight } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { currentMonth } from '@shared/utils/format.utils';

@Component({
  selector: 'dp-month-picker',
  standalone: true,
  imports: [LucideAngularModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="inline-flex items-center rounded-[var(--radius-md)] border border-[var(--border-default)]
                bg-[var(--bg-primary)]">
      <button
        type="button"
        (click)="shiftMonth(-1)"
        class="flex cursor-pointer items-center justify-center rounded-l-[var(--radius-md)] p-2
               text-[var(--text-secondary)] transition-colors
               hover:bg-[var(--bg-secondary)] hover:text-[var(--text-primary)]"
        aria-label="Previous month"
      >
        <lucide-icon [img]="ChevronLeftIcon" [size]="16" />
      </button>

      <button
        type="button"
        (click)="goToCurrentMonth()"
        class="flex cursor-pointer items-center gap-2 border-x border-[var(--border-default)] px-3 py-1.5
               text-[length:var(--text-sm)] font-medium text-[var(--text-primary)] transition-colors
               hover:bg-[var(--bg-secondary)]"
        [title]="'month_picker.today' | translate"
      >
        <lucide-icon [img]="CalendarIcon" [size]="14" class="text-[var(--text-tertiary)]" />
        {{ displayLabel() }}
      </button>

      <button
        type="button"
        (click)="shiftMonth(1)"
        class="flex cursor-pointer items-center justify-center rounded-r-[var(--radius-md)] p-2
               text-[var(--text-secondary)] transition-colors
               hover:bg-[var(--bg-secondary)] hover:text-[var(--text-primary)]"
        aria-label="Next month"
      >
        <lucide-icon [img]="ChevronRightIcon" [size]="16" />
      </button>
    </div>
  `,
})
export class MonthPickerComponent {
  private readonly translate = inject(TranslateService);

  readonly value = input.required<string>();
  readonly valueChange = output<string>();

  protected readonly CalendarIcon = Calendar;
  protected readonly ChevronLeftIcon = ChevronLeft;
  protected readonly ChevronRightIcon = ChevronRight;

  protected readonly displayLabel = computed(() => {
    const [year, month] = this.value().split('-').map(Number);
    const monthKey = `month.${month - 1}`;
    return `${this.translate.instant(monthKey)} ${year}`;
  });

  shiftMonth(delta: number): void {
    const [year, month] = this.value().split('-').map(Number);
    const d = new Date(year, month - 1 + delta, 1);
    const next = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    this.valueChange.emit(next);
  }

  goToCurrentMonth(): void {
    const now = currentMonth();
    if (this.value() !== now) {
      this.valueChange.emit(now);
    }
  }
}
