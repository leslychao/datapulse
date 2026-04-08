import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
} from '@angular/core';

import { AgGridAngular } from 'ag-grid-angular';
import {
  CellContextMenuEvent,
  ColDef,
  ColumnMovedEvent,
  ColumnResizedEvent,
  ColumnVisibleEvent,
  GetContextMenuItemsParams,
  GetRowIdParams,
  GridApi,
  GridReadyEvent,
  MenuItemDef,
  RowClassRules,
  RowClickedEvent,
  RowDoubleClickedEvent,
  RowDataUpdatedEvent,
  RowSelectionOptions,
  SelectionChangedEvent,
  SortChangedEvent,
  PaginationChangedEvent,
} from 'ag-grid-community';

import { AG_GRID_LOCALE_RU } from '@shared/config/ag-grid-locale';
import { ViewStateService, PersistedColumnState } from '@shared/services/view-state.service';

const DEFAULT_COL_DEF: ColDef = {
  resizable: true,
};

@Component({
  selector: 'dp-data-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgGridAngular],
  template: `
    <div [style.height]="height()" class="w-full" [class.dp-grid-clickable]="clickableRows()">
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
          [suppressCellFocus]="suppressCellFocus()"
          [rowClassRules]="rowClassRules()"
          [stopEditingWhenCellsLoseFocus]="stopEditingWhenCellsLoseFocus()"
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
          (columnResized)="onColumnStateChanged($event)"
          (columnMoved)="onColumnStateChanged($event)"
          (columnVisible)="onColumnStateChanged($event)"
          [suppressContextMenu]="!getContextMenuItems()"
          [getContextMenuItems]="getContextMenuItems()"
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

  readonly rowClassRules = input<RowClassRules>({});
  readonly clickableRows = input(false);
  readonly suppressCellFocus = input(true);
  readonly stopEditingWhenCellsLoseFocus = input(false);
  readonly enableFlash = input(false);
  readonly contextMenuEnabled = input(false);
  readonly getContextMenuItems = input<(params: GetContextMenuItemsParams) => (string | MenuItemDef)[]>();
  readonly initialSortModel = input<{ colId: string; sort: 'asc' | 'desc' }[]>([]);
  readonly viewStateKey = input<string>();

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
  private readonly viewStateService = inject(ViewStateService);
  private previousRowIds = new Set<string>();
  private gridApi: GridApi | null = null;
  private columnSaveTimer: ReturnType<typeof setTimeout> | null = null;

  getApi(): GridApi | null {
    return this.gridApi;
  }

  onGridReady(event: GridReadyEvent<any>): void {
    this.gridApi = event.api;
    const sortModel = this.initialSortModel();
    if (sortModel.length > 0) {
      event.api.applyColumnState({
        state: sortModel,
        defaultState: { sort: null },
      });
    }
    this.restoreColumnState(event.api);
    this.gridReady.emit(event.api);
  }

  onRowClicked(event: RowClickedEvent<any>): void {
    const target = event.event?.target as HTMLElement | null;
    if (target?.closest('[data-action]')) return;
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
    const sorted = event.api.getColumnState()
      ?.filter((c) => c.sort)
      .map((c) => ({ column: c.colId ?? '', direction: c.sort ?? '' }));
    if (sorted?.length) {
      this.sortChanged.emit(sorted[0]);
    } else {
      this.sortChanged.emit({ column: '', direction: '' });
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

  onColumnStateChanged(_event: ColumnResizedEvent | ColumnMovedEvent | ColumnVisibleEvent): void {
    if (!this.viewStateKey() || !this.gridApi) return;
    if (this.columnSaveTimer) clearTimeout(this.columnSaveTimer);
    this.columnSaveTimer = setTimeout(() => this.persistColumnState(), 500);
  }

  private persistColumnState(): void {
    const key = this.viewStateKey();
    if (!key || !this.gridApi) return;
    const cols = this.gridApi.getColumnState();
    const columnState: PersistedColumnState[] = cols
      .filter((c) => c.colId !== '__selection__')
      .map((c) => ({
        colId: c.colId ?? '',
        width: c.width ?? undefined,
        hide: c.hide ?? undefined,
        pinned: typeof c.pinned === 'string' ? c.pinned : c.pinned ? 'left' : undefined,
      }));
    this.viewStateService.save(key, { columnState });
  }

  private restoreColumnState(api: GridApi): void {
    const key = this.viewStateKey();
    if (!key) return;
    const persisted = this.viewStateService.restoreColumnState(key);
    if (!persisted?.length) return;
    const stateMap = new Map(persisted.map((c) => [c.colId, c]));
    const currentCols = api.getColumnState();
    const merged = currentCols.map((col) => {
      const saved = stateMap.get(col.colId ?? '');
      if (!saved) return col;
      const pinned = saved.pinned !== undefined
        ? (saved.pinned as 'left' | 'right' | null)
        : col.pinned;
      return { ...col, width: saved.width ?? col.width, hide: saved.hide ?? col.hide, pinned };
    });
    api.applyColumnState({ state: merged, applyOrder: true });
  }
}
