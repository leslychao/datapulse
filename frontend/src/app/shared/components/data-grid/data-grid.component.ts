import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { AgGridAngular } from 'ag-grid-angular';

const AG_GRID_LOCALE_RU: Record<string, string> = {
  page: 'Страница',
  of: 'из',
  to: 'по',
  next: 'Вперёд',
  previous: 'Назад',
  loadingOoo: 'Загрузка...',
  noRowsToShow: 'Нет данных для отображения',
  filterOoo: 'Фильтр...',
  equals: 'Равно',
  notEqual: 'Не равно',
  contains: 'Содержит',
  notContains: 'Не содержит',
  startsWith: 'Начинается с',
  endsWith: 'Заканчивается на',
  pageSizeSelectorLabel: 'Строк на странице:',
  ariaFilterInput: 'Фильтр',
};

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
export class DataGridComponent {
  readonly columnDefs = input<any[]>([]);
  readonly rowData = input<any[]>([]);
  readonly loading = input(false);
  readonly pagination = input(true);
  readonly pageSize = input(25);
  readonly rowSelection = input<'single' | 'multiple' | undefined>(undefined);
  readonly getRowId = input<((params: any) => string) | undefined>(undefined);
  readonly height = input('500px');

  readonly rowClicked = output<any>();
  readonly selectionChanged = output<any[]>();
  readonly sortChanged = output<{ column: string; direction: string }>();
  readonly pageChanged = output<{ page: number; pageSize: number }>();

  protected readonly localeText = AG_GRID_LOCALE_RU;

  onRowClicked(event: any): void {
    this.rowClicked.emit(event.data);
  }

  onSelectionChanged(event: any): void {
    const rows = event.api.getSelectedRows();
    this.selectionChanged.emit(rows);
  }

  onSortChanged(event: any): void {
    const sortModel = event.api.getColumnState()
      ?.filter((c: any) => c.sort)
      .map((c: any) => ({ column: c.colId, direction: c.sort }));
    if (sortModel?.length) {
      this.sortChanged.emit(sortModel[0]);
    }
  }

  onPageChanged(event: any): void {
    const api = event.api;
    this.pageChanged.emit({
      page: api.paginationGetCurrentPage(),
      pageSize: api.paginationGetPageSize(),
    });
  }
}
