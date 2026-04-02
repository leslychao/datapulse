import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { LucideAngularModule, Download, Columns3, AlignJustify, Pencil, X } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';

import { GridStore } from '@shared/stores/grid.store';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { SearchInputComponent } from '@shared/components/form/search-input.component';

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
    <div class="flex items-center justify-between border-b border-[var(--border-default)] px-6 py-2">
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

  readonly exportClicked = output<void>();

  protected readonly gridFilters: FilterConfig[] = [
    {
      key: 'marketplaceType',
      label: 'grid.filter.marketplace',
      type: 'multi-select',
      options: [
        { value: 'WB', label: 'Wildberries' },
        { value: 'OZON', label: 'Ozon' },
      ],
    },
    {
      key: 'status',
      label: 'grid.filter.status',
      type: 'multi-select',
      options: [
        { value: 'ACTIVE', label: 'grid.offer_status.ACTIVE' },
        { value: 'ARCHIVED', label: 'grid.offer_status.ARCHIVED' },
        { value: 'BLOCKED', label: 'grid.offer_status.BLOCKED' },
      ],
    },
    {
      key: 'lastDecision',
      label: 'grid.filter.decision',
      type: 'multi-select',
      options: [
        { value: 'CHANGE', label: 'grid.decision.CHANGE' },
        { value: 'SKIP', label: 'grid.decision.SKIP' },
        { value: 'HOLD', label: 'grid.decision.HOLD' },
      ],
    },
    {
      key: 'lastActionStatus',
      label: 'grid.filter.action_status',
      type: 'multi-select',
      options: [
        { value: 'PENDING_APPROVAL', label: 'grid.action_status.PENDING_APPROVAL' },
        { value: 'FAILED', label: 'grid.action_status.FAILED' },
        { value: 'SUCCEEDED', label: 'grid.action_status.SUCCEEDED' },
        { value: 'ON_HOLD', label: 'grid.action_status.ON_HOLD' },
      ],
    },
  ];

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
