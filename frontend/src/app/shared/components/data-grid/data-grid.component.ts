import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { AgGridAngular } from 'ag-grid-angular';
import { ColDef, GetRowIdParams, GridReadyEvent, RowClickedEvent, SelectionChangedEvent, SortChangedEvent, PaginationChangedEvent } from 'ag-grid-community';

import { AG_GRID_LOCALE_RU } from '@shared/config/ag-grid-locale';

@Component({
  selector: 'dp-data-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgGridAngular],
  template: `
    <div [style.height]="height()" class="w-full">
      @if (loading()) {
        <div class="flex h-full items-center justify-center">
          <span
            class="dp-spinner inline-block h-8 w-8 rounded-full border-2 border-[var(--border-default)]"
            style="border-top-color: var(--accent-primary)"
          ></span>
        </div>
      } @else {
        <ag-grid-angular
          class="ag-theme-alpine h-full w-full"
          [columnDefs]="columnDefs()"
          [rowData]="rowData()"
          [pagination]="pagination()"
          [paginationPageSize]="pageSize()"
          [rowSelection]="rowSelection()"
          [getRowId]="getRowId()"
          [suppressCellFocus]="true"
          [animateRows]="false"
          [localeText]="localeText"
          (rowClicked)="onRowClicked($event)"
          (selectionChanged)="onSelectionChanged($event)"
          (sortChanged)="onSortChanged($event)"
          (paginationChanged)="onPageChanged($event)"
        ></ag-grid-angular>
      }
    </div>
  `,
})
export class DataGridComponent<T = unknown> {
  readonly columnDefs = input<ColDef<T>[]>([]);
  readonly rowData = input<T[]>([]);
  readonly loading = input(false);
  readonly pagination = input(true);
  readonly pageSize = input(50);
  readonly rowSelection = input<'single' | 'multiple' | undefined>(undefined);
  readonly getRowId = input<((params: GetRowIdParams<T>) => string) | undefined>(undefined);
  readonly height = input('500px');
  readonly totalRows = input(0);
  readonly density = input<'compact' | 'comfortable' | 'normal'>('normal');
  readonly selectable = input(false);

  readonly rowClicked = output<T>();
  readonly cellDoubleClicked = output<T>();
  readonly selectionChanged = output<T[]>();
  readonly sortChanged = output<{ column: string; direction: string }>();
  readonly pageChanged = output<{ page: number; pageSize: number }>();

  protected readonly localeText = AG_GRID_LOCALE_RU;

  onRowClicked(event: RowClickedEvent<T>): void {
    if (event.data) this.rowClicked.emit(event.data);
  }

  onSelectionChanged(event: SelectionChangedEvent<T>): void {
    const rows = event.api.getSelectedRows();
    this.selectionChanged.emit(rows);
  }

  onSortChanged(event: SortChangedEvent<T>): void {
    const sortModel = event.api.getColumnState()
      ?.filter((c) => c.sort)
      .map((c) => ({ column: c.colId ?? '', direction: c.sort ?? '' }));
    if (sortModel?.length) {
      this.sortChanged.emit(sortModel[0]);
    }
  }

  onPageChanged(event: PaginationChangedEvent<T>): void {
    const api = event.api;
    this.pageChanged.emit({
      page: api.paginationGetCurrentPage(),
      pageSize: api.paginationGetPageSize(),
    });
  }
}
