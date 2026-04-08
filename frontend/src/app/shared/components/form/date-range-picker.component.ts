import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { LucideAngularModule, Calendar } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';

interface Preset {
  labelKey: string;
  days?: number;
  quarter?: boolean;
}

const PRESETS: Preset[] = [
  { labelKey: 'date_range.preset.7d', days: 7 },
  { labelKey: 'date_range.preset.30d', days: 30 },
  { labelKey: 'date_range.preset.90d', days: 90 },
  { labelKey: 'date_range.preset.quarter', quarter: true },
];

function toIsoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return toIsoDate(d);
}

function quarterRange(): { from: string; to: string } {
  const now = new Date();
  const qMonth = Math.floor(now.getMonth() / 3) * 3;
  const from = new Date(now.getFullYear(), qMonth, 1);
  const endOfQuarter = new Date(now.getFullYear(), qMonth + 3, 0);
  const to = endOfQuarter > now ? now : endOfQuarter;
  return { from: toIsoDate(from), to: toIsoDate(to) };
}

@Component({
  selector: 'dp-date-range-picker',
  standalone: true,
  imports: [LucideAngularModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="inline-flex items-center gap-2">
      <div class="inline-flex items-center rounded-[var(--radius-md)] border border-[var(--border-default)]
                  bg-[var(--bg-primary)]">
        @for (p of presets; track p.labelKey) {
          <button
            type="button"
            (click)="applyPreset(p)"
            class="h-8 cursor-pointer border-r border-[var(--border-default)] px-2.5
                   text-[length:var(--text-xs)] transition-colors first:rounded-l-[var(--radius-md)]"
            [class]="isPresetActive(p)
              ? 'bg-[var(--accent-primary)] text-white'
              : 'text-[var(--text-secondary)] hover:bg-[var(--bg-secondary)] hover:text-[var(--text-primary)]'"
          >
            {{ p.labelKey | translate }}
          </button>
        }
      </div>

      <div class="inline-flex items-center gap-1 rounded-[var(--radius-md)] border
                  border-[var(--border-default)] bg-[var(--bg-primary)] px-2.5 py-1">
        <lucide-icon [img]="CalendarIcon" [size]="14" class="text-[var(--text-tertiary)]" />
        <input
          type="date"
          [value]="from()"
          [max]="to() || today()"
          (change)="onFromChange($event)"
          class="h-6 border-none bg-transparent text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none"
        />
        <span class="text-[var(--text-tertiary)]">–</span>
        <input
          type="date"
          [value]="to()"
          [max]="today()"
          [min]="from()"
          (change)="onToChange($event)"
          class="h-6 border-none bg-transparent text-[length:var(--text-sm)] text-[var(--text-primary)]
                 outline-none"
        />
      </div>

      @if (rangeError()) {
        <span class="text-[length:var(--text-xs)] text-[var(--status-error)]">
          {{ 'form.date_range_error' | translate }}
        </span>
      }
    </div>
  `,
})
export class DateRangePickerComponent {
  readonly from = input.required<string>();
  readonly to = input.required<string>();
  readonly fromChange = output<string>();
  readonly toChange = output<string>();

  protected readonly CalendarIcon = Calendar;
  protected readonly presets = PRESETS;
  protected readonly today = computed(() => toIsoDate(new Date()));

  protected readonly rangeError = computed(() => {
    const f = this.from();
    const t = this.to();
    if (!f || !t) return false;
    return f > t;
  });

  protected isPresetActive(p: Preset): boolean {
    if (p.quarter) {
      const q = quarterRange();
      return this.from() === q.from && this.to() === q.to;
    }
    return this.from() === daysAgo(p.days!) && this.to() === daysAgo(0);
  }

  protected applyPreset(p: Preset): void {
    if (p.quarter) {
      const q = quarterRange();
      this.fromChange.emit(q.from);
      this.toChange.emit(q.to);
    } else {
      this.fromChange.emit(daysAgo(p.days!));
      this.toChange.emit(daysAgo(0));
    }
  }

  protected onFromChange(event: Event): void {
    this.fromChange.emit((event.target as HTMLInputElement).value);
  }

  protected onToChange(event: Event): void {
    this.toChange.emit((event.target as HTMLInputElement).value);
  }
}
