import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

export interface ToggleableColumn {
  colId: string;
  headerName: string;
  visible: boolean;
}

@Component({
  selector: 'dp-mismatch-toolbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <div class="flex items-center justify-between px-4 pb-1 pt-2">
      <span class="text-sm text-[var(--text-secondary)]">
        {{ 'mismatches.pagination.showing' | translate: { range: paginationLabel(), total: totalElements() } }}
      </span>

      <div class="flex items-center gap-2">
        <!-- Columns toggle -->
        <div class="relative">
          <button
            type="button"
            (click)="columnsOpen.set(!columnsOpen()); $event.stopPropagation()"
            class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)]
                   px-3 py-1.5 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]"
          >
            {{ 'mismatches.toolbar.columns' | translate }}
          </button>
          @if (columnsOpen()) {
            <div class="fixed inset-0 z-40" (click)="columnsOpen.set(false)"></div>
            <div
              class="absolute right-0 top-full z-50 mt-1 w-56
                     rounded-[var(--radius-md)] border border-[var(--border-default)]
                     bg-[var(--bg-primary)] p-2 shadow-[var(--shadow-md)]"
            >
              @for (col of toggleableColumns(); track col.colId) {
                <label
                  class="flex cursor-pointer items-center gap-2 rounded px-2 py-1
                         text-sm text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]"
                >
                  <input
                    type="checkbox"
                    [checked]="col.visible"
                    (change)="columnToggled.emit(col.colId)"
                    class="accent-[var(--accent-primary)]"
                  />
                  {{ col.headerName }}
                </label>
              }
            </div>
          }
        </div>

        <!-- Export -->
        <button
          type="button"
          (click)="exportClicked.emit()"
          class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)]
                 px-3 py-1.5 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]"
        >
          {{ 'mismatches.toolbar.export' | translate }}
        </button>

        <!-- Charts toggle -->
        <button
          type="button"
          (click)="chartsToggled.emit()"
          class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)]
                 px-3 py-1.5 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)]"
        >
          {{
            (chartsCollapsed()
              ? 'mismatches.toolbar.charts_show'
              : 'mismatches.toolbar.charts_hide') | translate
          }}
        </button>
      </div>
    </div>
  `,
})
export class MismatchToolbarComponent {
  readonly paginationLabel = input('');
  readonly totalElements = input(0);
  readonly toggleableColumns = input<ToggleableColumn[]>([]);
  readonly chartsCollapsed = input(true);

  readonly columnToggled = output<string>();
  readonly exportClicked = output<void>();
  readonly chartsToggled = output<void>();

  protected readonly columnsOpen = signal(false);
}
