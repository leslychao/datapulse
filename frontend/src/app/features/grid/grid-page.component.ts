import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';

import { OfferApiService } from '@core/api/offer-api.service';
import { OfferSummary } from '@core/models';
import { WebSocketService } from '@core/websocket/websocket.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { GridStore } from '@shared/stores/grid.store';
import { DetailPanelService } from '@shared/services/detail-panel.service';
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
import { buildGridColumnDefs } from './components/grid-column-defs';

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

      <dp-view-tabs />

      <dp-grid-toolbar (exportClicked)="exportData()" />

      @if (gridStore.draftMode() && gridStore.hasDraftChanges()) {
        <dp-draft-banner />
      }

      @if (offersQuery.isPending()) {
        <div class="flex-1 overflow-auto p-4">
          <dp-loading-skeleton [type]="'table-row'" [lines]="10" />
        </div>
      } @else if (offersQuery.isError()) {
        <div class="flex flex-1 items-center justify-center p-4">
          <dp-empty-state
            [message]="'grid.error_title' | translate"
            [hint]="'grid.error_hint' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="offersQuery.refetch()"
          />
        </div>
      } @else if (isEmpty()) {
        <div class="flex flex-1 items-center justify-center p-4">
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
        <div class="flex-1 overflow-hidden">
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="false"
            [pagination]="false"
            [rowSelection]="'multiple'"
            [getRowId]="getRowId"
            [height]="'100%'"
            (rowClicked)="onRowClicked($event)"
            (selectionChanged)="onSelectionChanged($event)"
            (sortChanged)="onSortChanged($event)"
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
          <dp-bulk-actions-bar />
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

  protected readonly columnDefs = buildGridColumnDefs();

  protected readonly getRowId = (params: any): string => String(params.data.id);

  protected readonly showDraftExitConfirm = signal(false);

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

  protected readonly rows = computed(() =>
    this.offersQuery.data()?.content ?? [],
  );

  protected readonly totalItems = computed(() =>
    this.offersQuery.data()?.totalElements ?? 0,
  );

  protected readonly isEmpty = computed(() =>
    !this.offersQuery.isPending() && this.rows().length === 0,
  );

  ngOnInit(): void {
    this.subscribeToGridUpdates();
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
    this.detailPanelService.open('offer', row.id);
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      this.router.navigate([], {
        queryParams: { offerId: row.id },
        queryParamsHandling: 'merge',
      });
    }
  }

  onSelectionChanged(rows: OfferSummary[]): void {
    this.gridStore.selectOffers(rows.map((r) => r.id));
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

    this.offerApi.exportOffers(wsId, this.gridStore.filters()).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      const dateStr = new Date().toISOString().slice(0, 10);
      a.href = url;
      a.download = `datapulse-export-${dateStr}.csv`;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  navigateToSettings(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      this.router.navigate(['/workspace', wsId, 'settings', 'connections']);
    }
  }

  protected draftExitMessage(): string {
    return `Отменить ${this.gridStore.draftCount()} изменений?`;
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
