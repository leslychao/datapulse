import { ChangeDetectionStrategy, Component, computed, effect, HostListener, inject, NgZone, OnDestroy, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { GetContextMenuItemsParams, GridApi, MenuItemDef, RowClassRules } from 'ag-grid-community';

import { OfferApiService } from '@core/api/offer-api.service';
import { OfferSummary } from '@core/models';
import { WebSocketService } from '@core/websocket/websocket.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { GridStore } from '@shared/stores/grid.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
import { ToastService } from '@shared/shell/toast/toast.service';
import { GuidedTourService } from '@shared/services/guided-tour.service';
import { TourProgressStore } from '@shared/stores/tour-progress.store';
import { GRID_BASICS_TOUR } from './tours/grid-tours';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { LoadingSkeletonComponent } from '@shared/components/loading-skeleton.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
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

      <dp-grid-toolbar (exportClicked)="exportData()" (draftToggle)="onDraftToggle()">
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
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="false"
            [pagination]="false"
            [rowSelection]="rowSelectionConfig"
            [getRowId]="getRowId"
            [rowClassRules]="activeRowClassRules"
            [height]="'100%'"
            [suppressCellFocus]="!gridStore.draftMode()"
            [getContextMenuItems]="contextMenuItems()"
            [clickableRows]="true"
            (rowClicked)="onRowClicked($event)"
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
  `,
})
export class GridPageComponent implements OnInit, OnDestroy {
  private readonly offerApi = inject(OfferApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly detailPanelService = inject(DetailPanelService);
  private readonly webSocket = inject(WebSocketService);
  private readonly queryClient = injectQueryClient();
  protected readonly gridStore = inject(GridStore);
  private readonly zone = inject(NgZone);

  private gridApi: GridApi | null = null;

  private readonly gridCallbacks: GridColumnCallbacks = {
    onLockToggle: (offerId, locked, currentPrice) =>
      this.zone.run(() => this.toggleLock(offerId, locked, currentPrice)),
    onDraftPriceChange: (offerId, newPrice, originalPrice, costPrice) =>
      this.zone.run(() => this.handleDraftPriceChange(offerId, newPrice, originalPrice, costPrice)),
    getDraftChange: (offerId) => this.gridStore.draftChanges().get(offerId),
  };

  protected readonly columnDefs = computed(() =>
    buildGridColumnDefs(this.gridCallbacks, this.gridStore.draftMode()),
  );

  protected readonly rowSelectionConfig = {
    mode: 'multiRow' as const,
    checkboxes: true,
    headerCheckbox: true,
    enableClickSelection: false,
  };

  protected readonly getRowId = (params: any): string => String(params.data.offerId);

  protected readonly activeRowClassRules: RowClassRules = {
    'dp-row-active': (params) =>
      this.detailPanelService.isOpen()
      && params.data?.offerId === this.detailPanelService.entityId(),
  };

  private readonly activeRowEffect = effect(() => {
    this.detailPanelService.entityId();
    this.detailPanelService.isOpen();
    this.gridApi?.redrawRows();
  });

  protected readonly showDraftExitConfirm = signal(false);

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
      setTimeout(() => this.tourService.start(GRID_BASICS_TOUR), 1200);
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
    this.detailPanelService.open('offer', row.offerId);
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      this.router.navigate([], {
        queryParams: { offerId: row.offerId },
        queryParamsHandling: 'merge',
      });
    }
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
