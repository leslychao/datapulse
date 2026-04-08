import { ChangeDetectionStrategy, Component, computed, effect, HostListener, inject, NgZone, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { GetContextMenuItemsParams, GridApi, MenuItemDef } from 'ag-grid-community';

import { OfferApiService } from '@core/api/offer-api.service';
import { CostProfileApiService } from '@core/api/cost-profile-api.service';
import {
  BulkFormulaCostResponse,
  CostProfileImportResult,
  OfferFilter,
  OfferSummary,
} from '@core/models';
import { RbacService } from '@core/auth/rbac.service';
import { WebSocketService } from '@core/websocket/websocket.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ViewStateService } from '@shared/services/view-state.service';
import { GridStore } from '@shared/stores/grid.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { GuidedTourService } from '@shared/services/guided-tour.service';
import { TourProgressStore } from '@shared/stores/tour-progress.store';
import { GRID_BASICS_TOUR } from './tours/grid-tours';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { LoadingSkeletonComponent } from '@shared/components/loading-skeleton.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { FormModalComponent } from '@shared/components/form-modal.component';
import { KpiStripComponent } from './components/kpi-strip.component';
import { ViewTabsComponent } from './components/view-tabs.component';
import { GridToolbarComponent } from './components/grid-toolbar.component';
import { BulkActionsBarComponent } from './components/bulk-actions-bar.component';
import { DraftBannerComponent } from './components/draft-banner.component';
import { buildGridColumnDefs, GridColumnCallbacks } from './components/grid-column-defs';

@Component({
  selector: 'dp-grid-page',
  standalone: true,
  imports: [
    TranslatePipe,
    DataGridComponent,
    PaginationBarComponent,
    EmptyStateComponent,
    LoadingSkeletonComponent,
    ConfirmationModalComponent,
    FormModalComponent,
    KpiStripComponent,
    ViewTabsComponent,
    GridToolbarComponent,
    BulkActionsBarComponent,
    DraftBannerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex h-full flex-col overflow-hidden">
      <dp-kpi-strip />

      <dp-grid-toolbar
        (exportClicked)="exportData()"
        (draftToggle)="onDraftToggle()"
        (costImportFile)="importCostCsv($event)"
        (costExportClicked)="exportCostCsv()"
      >
        <dp-view-tabs />
      </dp-grid-toolbar>

      @if (gridStore.draftMode() && gridStore.hasDraftChanges()) {
        <dp-draft-banner />
      }

      @if (offersQuery.isPending()) {
        <div class="flex-1 overflow-auto px-4 py-4">
          <dp-loading-skeleton [type]="'table-row'" [lines]="10" />
        </div>
      } @else if (offersQuery.isError()) {
        <div class="flex flex-1 items-center justify-center px-4 py-4">
          <dp-empty-state
            [message]="'grid.error_title' | translate"
            [hint]="'grid.error_hint' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="offersQuery.refetch()"
          />
        </div>
      } @else if (isEmpty()) {
        <div class="flex flex-1 items-center justify-center px-4 py-4">
          @if (gridStore.hasActiveFilters()) {
            <dp-empty-state
              [message]="'grid.empty' | translate"
              [actionLabel]="'grid.clear_filters' | translate"
              (action)="gridStore.resetFilters()"
            />
          } @else {
            <dp-empty-state
              [message]="'grid.no_data' | translate"
              [actionLabel]="'grid.go_settings' | translate"
              (action)="navigateToSettings()"
            />
          }
        </div>
      } @else {
        <div class="flex-1 overflow-hidden px-4 pt-2" data-tour="grid-table">
          <dp-data-grid
            viewStateKey="grid:offers"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="false"
            [pagination]="false"
            [rowSelection]="rowSelectionConfig"
            [getRowId]="getRowId"
            [rowClassRules]="{}"
            [height]="'100%'"
            [suppressCellFocus]="!gridStore.draftMode()"
            [stopEditingWhenCellsLoseFocus]="true"
            [getContextMenuItems]="contextMenuItems()"
            (selectionChanged)="onSelectionChanged($event)"
            (sortChanged)="onSortChanged($event)"
            (gridReady)="onGridReady($event)"
          />
        </div>

        <dp-pagination-bar
          [totalItems]="totalItems()"
          [pageSize]="gridStore.pageSize()"
          [currentPage]="gridStore.page()"
          [pageSizeOptions]="[50, 100, 200]"
          (pageChange)="onPageChange($event)"
        />

        @if (gridStore.hasSelection()) {
          <dp-bulk-actions-bar [selectedOffers]="selectedOffers()" />
        }
      }
    </div>

    <dp-confirmation-modal
      [open]="showDraftExitConfirm()"
      [title]="'draft.exit_title' | translate"
      [message]="draftExitMessage()"
      [confirmLabel]="'draft.exit_confirm' | translate"
      [danger]="true"
      (confirmed)="confirmDraftExit()"
      (cancelled)="cancelDraftExit()"
    />

    @if (showImportResult()) {
      <dp-form-modal
        [title]="'grid.cost.import_result_title' | translate"
        [submitLabel]="'actions.close' | translate"
        (submitted)="showImportResult.set(false)"
        (cancelled)="showImportResult.set(false)"
      >
        <div class="space-y-2 text-[length:var(--text-sm)]">
          <p class="text-[var(--text-primary)]">
            {{ 'grid.cost.import_imported' | translate }}: <strong>{{ importResult()?.imported }}</strong>
          </p>
          <p class="text-[var(--text-primary)]">
            {{ 'grid.cost.import_skipped' | translate }}: <strong>{{ importResult()?.skipped }}</strong>
          </p>
          @if (importResult()?.errors?.length) {
            <p class="text-[var(--status-error)]">
              {{ 'grid.cost.import_errors' | translate }}: {{ importResult()!.errors.length }}
            </p>
            <ul class="max-h-40 overflow-auto text-[length:var(--text-xs)] text-[var(--text-secondary)]">
              @for (err of importResult()!.errors; track err.row) {
                <li>{{ 'grid.cost.import_error_row' | translate }} {{ err.row }}: {{ err.message }}</li>
              }
            </ul>
          }
        </div>
      </dp-form-modal>
    }
  `,
})
export class GridPageComponent implements OnInit, OnDestroy {
  private readonly offerApi = inject(OfferApiService);
  private readonly costApi = inject(CostProfileApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly viewState = inject(ViewStateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly webSocket = inject(WebSocketService);
  private readonly queryClient = injectQueryClient();
  protected readonly gridStore = inject(GridStore);
  private readonly zone = inject(NgZone);
  private readonly rbac = inject(RbacService);

  private static readonly URL_FILTER_KEYS = [
    'marketplace', 'status', 'decision', 'actionStatus', 'search', 'sort', 'dir',
  ] as const;

  constructor() {
    this.restoreFiltersFromUrl();
  }

  private lastUrlParams = '';

  private readonly urlSyncEffect = effect(() => {
    const f = this.gridStore.filters();
    const sortCol = this.gridStore.sortColumn();
    const sortDir = this.gridStore.sortDirection();

    const params: Record<string, string> = {};
    if (f.marketplaceType?.length) params['marketplace'] = f.marketplaceType.join(',');
    if (f.status?.length) params['status'] = f.status.join(',');
    if (f.lastDecision?.length) params['decision'] = f.lastDecision.join(',');
    if (f.lastActionStatus?.length) params['actionStatus'] = f.lastActionStatus.join(',');
    if (f.skuCode) params['search'] = f.skuCode;

    const isDefaultSort = sortCol === 'skuCode' && sortDir === 'ASC';
    if (!isDefaultSort && sortCol) {
      params['sort'] = sortCol;
      params['dir'] = sortDir;
    }

    const serialized = JSON.stringify(params);
    if (serialized === this.lastUrlParams) return;
    this.lastUrlParams = serialized;

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      replaceUrl: true,
    });
  });

  private static readonly VIEW_STATE_KEY = 'grid:offers';

  private restoreFiltersFromUrl(): void {
    const qp = this.route.snapshot.queryParams;
    const hasUrl = GridPageComponent.URL_FILTER_KEYS.some((k) => qp[k]);

    if (hasUrl) {
      const filters: OfferFilter = {};
      if (qp['marketplace']) filters.marketplaceType = qp['marketplace'].split(',');
      if (qp['status']) filters.status = qp['status'].split(',');
      if (qp['decision']) filters.lastDecision = qp['decision'].split(',');
      if (qp['actionStatus']) filters.lastActionStatus = qp['actionStatus'].split(',');
      const search = qp['search'] ?? '';
      if (search) filters.skuCode = search;
      const sortColumn = qp['sort'] || 'skuCode';
      const sortDirection = (qp['dir']?.toUpperCase() === 'DESC' ? 'DESC' : 'ASC') as 'ASC' | 'DESC';
      this.gridStore.restoreUrlState(filters, search, sortColumn, sortDirection);
      return;
    }

    const persisted = this.viewState.restore(GridPageComponent.VIEW_STATE_KEY);
    if (persisted?.filters) {
      const pf = persisted.filters;
      const filters: OfferFilter = {};
      if (pf['marketplace']) filters.marketplaceType = pf['marketplace'];
      if (pf['status']) filters.status = pf['status'];
      if (pf['decision']) filters.lastDecision = pf['decision'];
      if (pf['actionStatus']) filters.lastActionStatus = pf['actionStatus'];
      const search = pf['search'] ?? '';
      if (search) filters.skuCode = search;
      const sort = persisted.sort;
      const sortColumn = sort?.column || 'skuCode';
      const sortDirection = (sort?.direction?.toUpperCase() === 'DESC' ? 'DESC' : 'ASC') as 'ASC' | 'DESC';
      this.gridStore.restoreUrlState(filters, search, sortColumn, sortDirection);
    }
  }

  private readonly viewStateSyncEffect = effect(() => {
    const f = this.gridStore.filters();
    const sortCol = this.gridStore.sortColumn();
    const sortDir = this.gridStore.sortDirection();
    const search = this.gridStore.searchTerm();
    const filters: Record<string, any> = {};
    if (f.marketplaceType?.length) filters['marketplace'] = f.marketplaceType;
    if (f.status?.length) filters['status'] = f.status;
    if (f.lastDecision?.length) filters['decision'] = f.lastDecision;
    if (f.lastActionStatus?.length) filters['actionStatus'] = f.lastActionStatus;
    if (search) filters['search'] = search;
    this.viewState.save(GridPageComponent.VIEW_STATE_KEY, {
      filters,
      sort: { column: sortCol ?? '', direction: sortDir.toLowerCase() as 'asc' | 'desc' },
    });
  });

  private gridApi: GridApi | null = null;

  private readonly gridCallbacks: GridColumnCallbacks = {
    onLockToggle: (offerId, locked, currentPrice) =>
      this.zone.run(() => this.toggleLock(offerId, locked, currentPrice)),
    onDraftPriceChange: (offerId, newPrice, originalPrice, costPrice) =>
      this.zone.run(() => this.handleDraftPriceChange(offerId, newPrice, originalPrice, costPrice)),
    getDraftChange: (offerId) => this.gridStore.draftChanges().get(offerId),
    onCostPriceChange: (sellerSkuId, offerId, newCostPrice) =>
      this.zone.run(() => this.handleCostPriceChange(sellerSkuId, offerId, newCostPrice)),
    canEditCost: this.rbac.canEditCostProfiles(),
    onNavigate: (offerId) =>
      this.zone.run(() => this.onRowClicked({ offerId } as OfferSummary)),
  };

  protected readonly columnDefs = computed(() =>
    buildGridColumnDefs(this.translate, this.gridCallbacks, this.gridStore.draftMode()),
  );

  protected readonly rowSelectionConfig = {
    mode: 'multiRow' as const,
    checkboxes: true,
    headerCheckbox: true,
    enableClickSelection: false,
  };

  protected readonly getRowId = (params: any): string => String(params.data.offerId);

  protected readonly showDraftExitConfirm = signal(false);
  protected readonly showImportResult = signal(false);
  protected readonly importResult = signal<CostProfileImportResult | null>(null);

  protected readonly contextMenuItems = computed(() => {
    if (!this.gridStore.draftMode()) return undefined;

    return (params: GetContextMenuItemsParams): (string | MenuItemDef)[] => {
      if (params.column?.getColId() !== 'currentPrice') return [];
      const offerId = params.node?.data?.offerId;
      if (!offerId) return [];
      const draft = this.gridStore.draftChanges().get(offerId);
      if (!draft) return [];

      return [{
        name: this.translate.instant('grid.draft.undo_change'),
        action: () => {
          this.gridStore.removeDraftPrice(offerId);
          if (params.node) {
            params.api.refreshCells({
              rowNodes: [params.node],
              columns: ['currentPrice', 'projectedMargin'],
              force: true,
            });
          }
        },
      }];
    };
  });

  protected onDraftToggle(): void {
    if (this.gridStore.draftMode() && this.gridStore.hasDraftChanges()) {
      this.showDraftExitConfirm.set(true);
    } else {
      this.gridStore.toggleDraftMode();
    }
  }

  readonly offersQuery = injectQuery(() => ({
    queryKey: [
      'offers',
      this.wsStore.currentWorkspaceId(),
      this.gridStore.filters(),
      this.gridStore.page(),
      this.gridStore.pageSize(),
      this.gridStore.sortColumn(),
      this.gridStore.sortDirection(),
    ],
    queryFn: () => lastValueFrom(
      this.offerApi.listOffers(
        this.wsStore.currentWorkspaceId()!,
        this.gridStore.filters(),
        this.gridStore.page(),
        this.gridStore.pageSize(),
        this.gridStore.sortColumn() ?? undefined,
        this.gridStore.sortDirection(),
      ),
    ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  protected readonly rows = computed(() => {
    const allRows = this.offersQuery.data()?.content ?? [];
    if (this.gridStore.showDraftOnly()) {
      const draftIds = this.gridStore.draftChanges();
      return allRows.filter(r => draftIds.has(r.offerId));
    }
    return allRows;
  });

  protected readonly totalItems = computed(() =>
    this.offersQuery.data()?.totalElements ?? 0,
  );

  protected readonly isEmpty = computed(() =>
    !this.offersQuery.isPending() && this.rows().length === 0,
  );

  protected readonly selectedOffers = computed(() => {
    const ids = this.gridStore.selectedOfferIds();
    if (ids.size === 0) return [];
    return this.rows().filter(r => ids.has(r.offerId));
  });

  private readonly tourService = inject(GuidedTourService);
  private readonly tourProgress = inject(TourProgressStore);

  ngOnInit(): void {
    this.subscribeToGridUpdates();
    if (GRID_BASICS_TOUR.triggerOnFirstVisit && !this.tourProgress.isCompleted(GRID_BASICS_TOUR.id)) {
      this.tourService.startWhenReady(GRID_BASICS_TOUR);
    }
  }

  ngOnDestroy(): void {
    if (this.gridStore.draftMode()) {
      this.gridStore.setDraftMode(false);
    }
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.gridStore.hasDraftChanges()) {
      event.preventDefault();
    }
  }

  onRowClicked(row: OfferSummary): void {
    this.router.navigate(['offer', row.offerId], { relativeTo: this.route });
  }

  onSelectionChanged(rows: OfferSummary[]): void {
    this.gridStore.selectOffers(rows.map((r) => r.offerId));
  }

  onSortChanged(sort: { column: string; direction: string }): void {
    this.gridStore.setSort(
      sort.column,
      sort.direction.toUpperCase() as 'ASC' | 'DESC',
    );
  }

  onPageChange(event: { page: number; pageSize: number }): void {
    if (event.pageSize !== this.gridStore.pageSize()) {
      this.gridStore.setPageSize(event.pageSize);
    } else {
      this.gridStore.setPage(event.page);
    }
  }

  exportData(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) return;

    const offerIds = this.gridStore.hasSelection()
      ? Array.from(this.gridStore.selectedOfferIds())
      : undefined;

    this.offerApi.exportOffers(wsId, this.gridStore.filters(), { offerIds }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        const dateStr = new Date().toISOString().slice(0, 10);
        a.href = url;
        a.download = `datapulse-export-${dateStr}.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.toast.error(this.translate.instant('grid.export_failed')),
    });
  }

  navigateToSettings(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      this.router.navigate(['/workspace', wsId, 'settings', 'connections']);
    }
  }

  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  readonly lockMutation = injectMutation(() => ({
    mutationFn: (params: { offerId: number; locked: boolean; currentPrice: number | null }) => {
      const wsId = this.wsStore.currentWorkspaceId()!;
      if (params.locked) {
        return lastValueFrom(this.offerApi.unlockOffer(wsId, params.offerId));
      }
      return lastValueFrom(
        this.offerApi.lockOffer(wsId, params.offerId, {
          lockedPrice: params.currentPrice ?? 0,
        }),
      );
    },
    onSuccess: () => {
      this.offersQuery.refetch();
      this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
    },
    onError: () => this.toast.error(this.translate.instant('grid.lock_toggle_error')),
  }));

  toggleLock(offerId: number, currentlyLocked: boolean, currentPrice: number | null): void {
    this.lockMutation.mutate({ offerId, locked: currentlyLocked, currentPrice });
  }

  private lastCostSavedOfferId: number | null = null;

  private readonly costSaveMutation = injectMutation(() => ({
    mutationFn: (params: { sellerSkuId: number; offerId: number; costPrice: number }) => {
      this.lastCostSavedOfferId = params.offerId;
      const today = new Date().toISOString().slice(0, 10);
      return lastValueFrom(this.costApi.bulkFormula({
        sellerSkuIds: [params.sellerSkuId],
        operation: 'FIXED',
        value: params.costPrice,
        validFrom: today,
      }));
    },
    onSuccess: (result: BulkFormulaCostResponse, params) => {
      if (result.updated > 0) {
        this.patchCostRowValues(params.offerId, params.costPrice);
      }
      this.showCostSaveToast(result);
      if (this.lastCostSavedOfferId != null) {
        const rowNode = this.gridApi?.getRowNode(String(this.lastCostSavedOfferId));
        if (rowNode) {
          this.gridApi?.flashCells({
            rowNodes: [rowNode],
            columns: ['costPrice', 'marginPct'],
          });
        }
      }
      this.offersQuery.refetch();
      this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
    },
    onError: () => this.toast.error(this.translate.instant('grid.cost.update_error')),
  }));

  private handleCostPriceChange(sellerSkuId: number, offerId: number, newCostPrice: number): void {
    this.patchCostRowValues(offerId, newCostPrice);
    this.costSaveMutation.mutate({ sellerSkuId, offerId, costPrice: newCostPrice });
  }

  private patchCostRowValues(offerId: number, costPrice: number): void {
    const rowNode = this.gridApi?.getRowNode(String(offerId));
    if (!rowNode?.data) return;

    rowNode.setDataValue('costPrice', costPrice);
    const currentPrice = rowNode.data.currentPrice as number | null;
    const marginPct =
      currentPrice && currentPrice > 0
        ? ((currentPrice - costPrice) / currentPrice) * 100
        : null;
    rowNode.setDataValue('marginPct', marginPct);

    this.gridApi?.refreshCells({
      rowNodes: [rowNode],
      columns: ['costPrice', 'marginPct'],
      force: true,
    });
  }

  private showCostSaveToast(result: BulkFormulaCostResponse): void {
    if (result.updated > 0) {
      if (result.skipped > 0) {
        this.toast.warning(this.translate.instant('grid.cost.update_partial', {
          updated: result.updated,
          skipped: result.skipped,
        }));
        return;
      }
      this.toast.success(this.translate.instant('grid.cost.update_success', { count: result.updated }));
      return;
    }

    const firstError = result.errors[0];
    if (firstError) {
      const translated = this.translate.instant(firstError);
      this.toast.error(translated === firstError ? firstError : translated);
      return;
    }

    this.toast.warning(this.translate.instant('grid.cost.update_partial', {
      updated: result.updated,
      skipped: result.skipped,
    }));
  }

  importCostCsv(file: File): void {
    const maxSize = 5 * 1024 * 1024;
    if (file.size > maxSize) {
      this.toast.error(this.translate.instant('grid.cost.file_too_large'));
      return;
    }
    lastValueFrom(this.costApi.importCsv(file)).then(
      (result) => {
        this.importResult.set(result);
        this.showImportResult.set(true);
        this.offersQuery.refetch();
        this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
      },
      () => this.toast.error(this.translate.instant('grid.cost.import_failed')),
    );
  }

  exportCostCsv(): void {
    this.toast.info(this.translate.instant('grid.cost.export_preparing'));
    lastValueFrom(this.costApi.exportCsv()).then(
      (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'cost-profiles.csv';
        a.click();
        window.URL.revokeObjectURL(url);
        this.toast.success(this.translate.instant('grid.cost.export_done'));
      },
      () => this.toast.error(this.translate.instant('grid.cost.error_export')),
    );
  }

  protected onGridReady(api: GridApi): void {
    this.gridApi = api;
  }

  private handleDraftPriceChange(
      offerId: number, newPrice: number, originalPrice: number, costPrice: number | null): void {
    if (newPrice === originalPrice) {
      this.gridStore.removeDraftPrice(offerId);
    } else {
      this.gridStore.setDraftPrice(offerId, newPrice, originalPrice, costPrice);
    }
    setTimeout(() => {
      const rowNode = this.gridApi?.getRowNode(String(offerId));
      if (rowNode) {
        this.gridApi?.refreshCells({
          rowNodes: [rowNode],
          columns: ['currentPrice', 'projectedMargin'],
          force: true,
        });
      }
    });
  }

  protected draftExitMessage(): string {
    return this.translate.instant('draft.exit_message', { count: this.gridStore.draftCount() });
  }

  protected confirmDraftExit(): void {
    this.gridStore.clearDraftChanges();
    this.gridStore.setDraftMode(false);
    this.showDraftExitConfirm.set(false);
  }

  protected cancelDraftExit(): void {
    this.showDraftExitConfirm.set(false);
  }

  private subscribeToGridUpdates(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) return;

    this.webSocket.subscribeTo(`/topic/workspace/${wsId}/grid-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
    });

    this.webSocket.subscribeTo(`/topic/workspace/${wsId}/kpi-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['grid-kpi'] });
    });

    this.webSocket.subscribeTo(`/topic/workspace/${wsId}/action-updates`, () => {
      this.queryClient.invalidateQueries({ queryKey: ['offers'] });
      this.queryClient.invalidateQueries({ queryKey: ['offer-detail'] });
      this.queryClient.invalidateQueries({ queryKey: ['action-history'] });
    });
  }
}
