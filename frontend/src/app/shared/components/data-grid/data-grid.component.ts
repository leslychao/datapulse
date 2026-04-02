import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';

import { AgGridAngular } from 'ag-grid-angular';
import {
  CellContextMenuEvent,
  ColDef,
  GetRowIdParams,
  GridApi,
  GridReadyEvent,
  RowClickedEvent,
  RowDoubleClickedEvent,
  RowDataUpdatedEvent,
  RowSelectionOptions,
  SelectionChangedEvent,
  SortChangedEvent,
  PaginationChangedEvent,
} from 'ag-grid-community';

import { AG_GRID_LOCALE_RU } from '@shared/config/ag-grid-locale';

const DEFAULT_COL_DEF: ColDef = {
  resizable: true,
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
          #agGrid
          class="ag-theme-alpine h-full w-full"
          [defaultColDef]="defaultColDef"
          [columnDefs]="columnDefs()"
          [rowData]="rowData()"
          [pagination]="pagination()"
          [paginationPageSize]="pageSize()"
          [rowSelection]="rowSelection()"
          [getRowId]="getRowId()"
          [selectionColumnDef]="selectionColumnDef"
          [suppressCellFocus]="true"
          [suppressRowClickSelection]="true"
          [alwaysShowVerticalScroll]="true"
          [animateRows]="false"
          [localeText]="localeText"
          (rowClicked)="onRowClicked($event)"
          (rowDoubleClicked)="onRowDoubleClicked($event)"
          (selectionChanged)="onSelectionChanged($event)"
          (sortChanged)="onSortChanged($event)"
          (paginationChanged)="onPageChanged($event)"
          (rowDataUpdated)="onRowDataUpdated($event)"
          (cellContextMenu)="onCellContextMenu($event)"
          (gridReady)="onGridReady($event)"
          [suppressContextMenu]="true"
        ></ag-grid-angular>
      }
    </div>
  `,
})
export class DataGridComponent {
  readonly columnDefs = input<ColDef[]>([]);
  readonly rowData = input<any[]>([]);
  readonly loading = input(false);
  readonly pagination = input(true);
  readonly pageSize = input(50);
  readonly rowSelection = input<'single' | 'multiple' | RowSelectionOptions<any> | undefined>(undefined);
  readonly getRowId = input<((params: GetRowIdParams) => string) | undefined>(undefined);
  readonly height = input('500px');
  readonly totalRows = input(0);
  readonly density = input<'compact' | 'comfortable' | 'normal'>('normal');
  readonly selectable = input(false);

  readonly enableFlash = input(false);
  readonly contextMenuEnabled = input(false);

  readonly rowClicked = output<any>();
  readonly cellDoubleClicked = output<any>();
  readonly selectionChanged = output<any[]>();
  readonly sortChanged = output<{ column: string; direction: string }>();
  readonly pageChanged = output<{ page: number; pageSize: number }>();
  readonly contextMenu = output<{ event: MouseEvent; data: any }>();
  readonly gridReady = output<GridApi>();

  protected readonly defaultColDef = DEFAULT_COL_DEF;
  protected readonly localeText = AG_GRID_LOCALE_RU;
  protected readonly selectionColumnDef = { pinned: 'left' as const, lockPosition: true };
  private previousRowIds = new Set<string>();
  private gridApi: GridApi | null = null;

  getApi(): GridApi | null {
    return this.gridApi;
  }

  onGridReady(event: GridReadyEvent<any>): void {
    this.gridApi = event.api;
    this.gridReady.emit(event.api);
  }

  onRowClicked(event: RowClickedEvent<any>): void {
    if (event.data) this.rowClicked.emit(event.data);
  }

  onRowDoubleClicked(event: RowDoubleClickedEvent<any>): void {
    if (event.data) this.cellDoubleClicked.emit(event.data);
  }

  onSelectionChanged(event: SelectionChangedEvent<any>): void {
    const rows = event.api.getSelectedRows();
    this.selectionChanged.emit(rows);
  }

  onSortChanged(event: SortChangedEvent<any>): void {
    const sortModel = event.api.getColumnState()
      ?.filter((c) => c.sort)
      .map((c) => ({ column: c.colId ?? '', direction: c.sort ?? '' }));
    if (sortModel?.length) {
      this.sortChanged.emit(sortModel[0]);
    }
  }

  onPageChanged(event: PaginationChangedEvent<any>): void {
    const api = event.api;
    this.pageChanged.emit({
      page: api.paginationGetCurrentPage(),
      pageSize: api.paginationGetPageSize(),
    });
  }

  onCellContextMenu(event: CellContextMenuEvent<any>): void {
    if (!this.contextMenuEnabled()) return;
    const mouseEvent = event.event as MouseEvent;
    mouseEvent.preventDefault();
    if (event.data) {
      this.contextMenu.emit({ event: mouseEvent, data: event.data });
    }
  }

  onRowDataUpdated(event: RowDataUpdatedEvent<any>): void {
    if (!this.enableFlash() || !this.getRowId()) return;
    this.gridApi = event.api;
    const currentIds = new Set<string>();
    event.api.forEachNode((node) => {
      if (node.data) {
        const id = this.getRowId()!({ data: node.data } as any);
        currentIds.add(id);
        if (!this.previousRowIds.has(id)) {
          const el = document.querySelector(`[row-id="${id}"]`);
          if (el) {
            el.classList.add('dp-flash');
            setTimeout(() => el.classList.remove('dp-flash'), 1100);
          }
        }
      }
    });
    this.previousRowIds = currentIds;
  }
}
