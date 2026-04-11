import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import {
  ColDef,
  GetRowIdParams,
  GridApi,
  RowClassRules,
  RowSelectionOptions,
} from 'ag-grid-community';

import { Mismatch, MismatchWsEvent } from '@core/models';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';

import { buildMismatchColumnDefs } from './mismatch-column-defs';

@Component({
  selector: 'dp-mismatch-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataGridComponent],
  template: `
    <div class="relative h-full">
      @if (refreshing()) {
        <div
          class="absolute inset-0 z-10 flex items-center justify-center
                 bg-[color-mix(in_srgb,var(--bg-primary)_70%,transparent)]"
        >
          <span
            class="dp-spinner inline-block h-10 w-10 rounded-full border-2
                   border-[var(--border-default)]"
            style="border-top-color: var(--accent-primary)"
          ></span>
        </div>
      }
      <dp-data-grid
        [columnDefs]="columnDefs"
        [rowData]="rows()"
        [loading]="initialLoading()"
        [pagination]="false"
        [rowSelection]="rowSelectionConfig"
        [getRowId]="getRowId"
        [rowClassRules]="rowClassRules"
        [viewStateKey]="'mismatches:grid'"
        [contextMenuEnabled]="true"
        height="100%"
        (selectionChanged)="selectionChanged.emit($event)"
        (sortChanged)="sortChanged.emit($event)"
        (contextMenu)="onContextMenu($event)"
        (gridReady)="onGridReady($event)"
      />
    </div>
  `,
})
export class MismatchGridComponent {
  private readonly translate = inject(TranslateService);

  readonly rows = input<Mismatch[]>([]);
  readonly loading = input(false);

  readonly offerClicked = output<Mismatch>();
  readonly quickAckClicked = output<Mismatch>();
  readonly selectionChanged = output<Mismatch[]>();
  readonly sortChanged = output<{ column: string; direction: string }>();
  readonly contextMenuEvent = output<{ x: number; y: number; row: Mismatch }>();
  readonly gridApiReady = output<GridApi>();

  protected readonly initialLoading = computed(
    () => this.loading() && this.rows().length === 0,
  );

  protected readonly refreshing = computed(
    () => this.loading() && this.rows().length > 0,
  );

  readonly columnDefs: ColDef<Mismatch>[];

  protected readonly getRowId = (params: GetRowIdParams<Mismatch>) =>
    String(params.data?.mismatchId ?? '');

  protected readonly rowSelectionConfig: RowSelectionOptions<Mismatch> = {
    mode: 'multiRow',
    checkboxes: true,
    headerCheckbox: true,
    enableClickSelection: false,
  };

  protected readonly rowClassRules: RowClassRules<Mismatch> = {
    'dp-critical-row': (params) => params.data?.severity === 'CRITICAL',
  };

  private gridApi: GridApi | null = null;

  constructor() {
    this.columnDefs = buildMismatchColumnDefs(this.translate, {
      onOfferClick: (row) => this.offerClicked.emit(row),
      onQuickAck: (row) => this.quickAckClicked.emit(row),
    });
  }

  applyPulseAnimation(evt: MismatchWsEvent): void {
    if (!this.gridApi) return;
    const rowNode = this.gridApi.getRowNode(String(evt.mismatchId));
    if (!rowNode) return;
    const el = document.querySelector<HTMLElement>(`[row-id="${rowNode.id}"]`);
    if (!el) return;
    const cssClass = evt.eventType === 'MISMATCH_DETECTED'
      ? 'dp-pulse-new'
      : evt.eventType === 'MISMATCH_RESOLVED'
        ? 'dp-pulse-resolved'
        : 'dp-pulse-acknowledged';
    el.classList.add(cssClass);
    setTimeout(() => el.classList.remove(cssClass), 2000);
  }

  toggleColumn(colId: string): void {
    if (!this.gridApi) return;
    const col = this.gridApi.getColumn(colId);
    if (!col) return;
    this.gridApi.setColumnsVisible([colId], !col.isVisible());
  }

  isColumnVisible(colId: string): boolean {
    if (!this.gridApi) return true;
    const col = this.gridApi.getColumn(colId);
    return col ? col.isVisible() : true;
  }

  deselectAll(): void {
    this.gridApi?.deselectAll();
  }

  protected onGridReady(api: GridApi): void {
    this.gridApi = api;
    this.gridApiReady.emit(api);
  }

  protected onContextMenu(event: { event: MouseEvent; data: Mismatch }): void {
    this.contextMenuEvent.emit({
      x: event.event.clientX,
      y: event.event.clientY,
      row: event.data,
    });
  }
}
