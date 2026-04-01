import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { LucideAngularModule, Download, Columns3, AlignJustify, Pencil, X } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';

import { GridStore } from '@shared/stores/grid.store';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { SearchInputComponent } from '@shared/components/form/search-input.component';

const GRID_FILTERS: FilterConfig[] = [
  {
    key: 'marketplaceType',
    label: 'Маркетплейс',
    type: 'multi-select',
    options: [
      { value: 'WB', label: 'Wildberries' },
      { value: 'OZON', label: 'Ozon' },
    ],
  },
  {
    key: 'status',
    label: 'Статус',
    type: 'multi-select',
    options: [
      { value: 'ACTIVE', label: 'Активный' },
      { value: 'ARCHIVED', label: 'Архив' },
      { value: 'BLOCKED', label: 'Заблокирован' },
    ],
  },
  {
    key: 'lastDecision',
    label: 'Решение',
    type: 'multi-select',
    options: [
      { value: 'CHANGE', label: 'Изменение' },
      { value: 'SKIP', label: 'Пропуск' },
      { value: 'HOLD', label: 'Удержание' },
    ],
  },
  {
    key: 'lastActionStatus',
    label: 'Статус действия',
    type: 'multi-select',
    options: [
      { value: 'PENDING_APPROVAL', label: 'Ожидает' },
      { value: 'FAILED', label: 'Ошибка' },
      { value: 'SUCCEEDED', label: 'Выполнено' },
      { value: 'ON_HOLD', label: 'Приостановлено' },
    ],
  },
];

@Component({
  selector: 'dp-grid-toolbar',
  standalone: true,
  imports: [
    LucideAngularModule,
    TranslatePipe,
    FilterBarComponent,
    SearchInputComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center justify-between border-b border-[var(--border-default)] px-4 py-2">
      <div class="flex items-center gap-3">
        <dp-search-input
          [placeholder]="'grid.search_placeholder' | translate"
          (searchChange)="onSearch($event)"
        />

        <dp-filter-bar
          [filters]="gridFilters"
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <div class="flex items-center gap-1.5">
        <button
          (click)="gridStore.toggleDraftMode()"
          class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] px-3 py-1.5 text-[length:var(--text-sm)] transition-colors"
          [class]="gridStore.draftMode()
            ? 'bg-[var(--accent-primary)] font-medium text-white'
            : 'text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]'"
        >
          <lucide-icon [img]="PencilIcon" [size]="14"></lucide-icon>
          {{ 'grid.toolbar.draft' | translate }}
        </button>

        <button
          (click)="gridStore.toggleDensity()"
          class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
          [attr.aria-label]="'grid.toolbar.density' | translate"
        >
          <lucide-icon [img]="DensityIcon" [size]="14"></lucide-icon>
          {{ 'grid.toolbar.density' | translate }}
        </button>

        <button
          (click)="exportClicked.emit()"
          class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        >
          <lucide-icon [img]="DownloadIcon" [size]="14"></lucide-icon>
          {{ 'grid.export' | translate }}
        </button>
      </div>
    </div>
  `,
})
export class GridToolbarComponent {
  protected readonly gridStore = inject(GridStore);

  protected readonly DownloadIcon = Download;
  protected readonly ColumnsIcon = Columns3;
  protected readonly DensityIcon = AlignJustify;
  protected readonly PencilIcon = Pencil;
  protected readonly CloseIcon = X;

  protected readonly gridFilters = GRID_FILTERS;

  readonly exportClicked = output<void>();

  protected filterValues(): Record<string, any> {
    const f = this.gridStore.filters();
    const vals: Record<string, any> = {};
    if (f.marketplaceType?.length) vals['marketplaceType'] = f.marketplaceType;
    if (f.status?.length) vals['status'] = f.status;
    if (f.lastDecision?.length) vals['lastDecision'] = f.lastDecision;
    if (f.lastActionStatus?.length) vals['lastActionStatus'] = f.lastActionStatus;
    return vals;
  }

  protected onFiltersChanged(values: Record<string, any>): void {
    this.gridStore.updateFilters({
      ...this.gridStore.filters(),
      marketplaceType: values['marketplaceType'] || undefined,
      status: values['status'] || undefined,
      lastDecision: values['lastDecision'] || undefined,
      lastActionStatus: values['lastActionStatus'] || undefined,
    });
  }

  protected onSearch(term: string): void {
    this.gridStore.setSearchTerm(term);
  }
}
