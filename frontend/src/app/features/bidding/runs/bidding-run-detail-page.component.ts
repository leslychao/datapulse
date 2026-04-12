import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  ColDef,
  GetRowIdParams,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { BidDecisionSummary, BiddingRunSummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import {
  formatMoney,
  formatDateTime,
  renderBadge,
} from '@shared/utils/format.utils';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import {
  FilterBarComponent,
  FilterConfig,
} from '@shared/components/filter-bar/filter-bar.component';
import { PaginationBarComponent } from '@shared/components/pagination-bar/pagination-bar.component';

const RUN_STATUS_COLOR: Record<string, string> = {
  RUNNING: 'var(--status-info)',
  COMPLETED: 'var(--status-success)',
  FAILED: 'var(--status-error)',
  PAUSED: 'var(--status-warning)',
};

const DECISION_COLOR: Record<string, string> = {
  BID_UP: 'var(--status-success)',
  BID_DOWN: 'var(--status-error)',
  HOLD: 'var(--status-neutral)',
  PAUSE: 'var(--status-warning)',
  RESUME: 'var(--status-info)',
  SET_MINIMUM: 'var(--status-info)',
  EMERGENCY_CUT: 'var(--status-error)',
};

@Component({
  selector: 'dp-bidding-run-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    KpiCardComponent,
    DataGridComponent,
    EmptyStateComponent,
    FilterBarComponent,
    PaginationBarComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <!-- Back button + run number -->
      <div class="flex items-center gap-3 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <button
          (click)="goBack()"
          class="cursor-pointer rounded-[var(--radius-sm)] px-2 py-1 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        >
          {{ 'bidding.runs.detail.back' | translate }}
        </button>
        @if (run()) {
          <span class="text-sm font-medium text-[var(--text-primary)]">
            {{ 'bidding.runs.detail.title' | translate }} #{{ run()!.id }}
          </span>
          <span
            class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium leading-4"
            [style.background-color]="'color-mix(in srgb, ' + statusCssVar() + ' 12%, transparent)'"
            [style.color]="statusCssVar()"
          >
            @if (run()!.status === 'RUNNING') {
              <span class="inline-block h-1.5 w-1.5 animate-pulse rounded-full" [style.background-color]="statusCssVar()"></span>
            } @else {
              <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="statusCssVar()"></span>
            }
            {{ ('bidding.runs.status.' + run()!.status) | translate }}
          </span>
        }
      </div>

      @if (runQuery.isPending()) {
        <div class="flex flex-1 items-center justify-center">
          <span
            class="dp-spinner inline-block h-8 w-8 rounded-full border-2 border-[var(--border-default)]"
            style="border-top-color: var(--accent-primary)"
          ></span>
        </div>
      } @else if (runQuery.isError()) {
        <div class="px-6 py-4">
          <dp-empty-state
            [message]="'bidding.runs.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="runQuery.refetch()"
          />
        </div>
      } @else if (run()) {
        <!-- KPI Strip -->
        <div class="flex gap-3 bg-[var(--bg-secondary)] px-4 py-2">
          <dp-kpi-card
            [label]="'bidding.runs.kpi.eligible' | translate"
            [value]="run()!.totalEligible"
            [loading]="false"
          />
          <dp-kpi-card
            [label]="'bidding.runs.kpi.bid_up' | translate"
            [value]="run()!.totalBidUp"
            [loading]="false"
            accent="success"
          />
          <dp-kpi-card
            [label]="'bidding.runs.kpi.bid_down' | translate"
            [value]="run()!.totalBidDown"
            [loading]="false"
            accent="error"
          />
          <dp-kpi-card
            [label]="'bidding.runs.kpi.hold' | translate"
            [value]="run()!.totalHold"
            [loading]="false"
            accent="neutral"
          />
          <dp-kpi-card
            [label]="'bidding.runs.kpi.pause' | translate"
            [value]="run()!.totalPause"
            [loading]="false"
            accent="warning"
          />
        </div>

        <!-- Meta info -->
        <div class="flex flex-wrap items-center gap-3 border-b border-[var(--border-default)] px-4 py-2">
          <span class="text-sm text-[var(--text-secondary)]">{{ timingDisplay() }}</span>
        </div>

        @if (run()!.status === 'FAILED') {
          <div class="mx-6 mt-3 rounded-[var(--radius-md)] border border-[var(--status-error)] bg-[color-mix(in_srgb,var(--status-error)_8%,transparent)] px-4 py-2.5 text-sm text-[var(--status-error)]">
            {{ 'bidding.runs.status.FAILED' | translate }}
          </div>
        }

        <!-- Decision filter -->
        <div class="border-b border-[var(--border-default)] px-4 py-2">
          <dp-filter-bar
            [filters]="decisionFilterConfigs"
            [values]="decisionFilterValues()"
            (filtersChanged)="onDecisionFiltersChanged($event)"
          />
        </div>

        <!-- Decisions Grid -->
        <div class="flex-1 px-4 py-2">
          @if (decisionsQuery.isError()) {
            <dp-empty-state
              [message]="'bidding.decisions.error' | translate"
              [actionLabel]="'actions.retry' | translate"
              (action)="decisionsQuery.refetch()"
            />
          } @else if (!decisionsQuery.isPending() && decisionRows().length === 0) {
            <dp-empty-state
              [message]="'bidding.decisions.empty' | translate"
            />
          } @else {
            <dp-data-grid
              viewStateKey="bidding:run-detail"
              [columnDefs]="decisionColumnDefs"
              [rowData]="decisionRows()"
              [loading]="decisionsQuery.isPending()"
              [pagination]="true"
              [pageSize]="100"
              [getRowId]="getRowId"
              [height]="'100%'"
            />
            <dp-pagination-bar
              [totalItems]="decisionsQuery.data()?.totalElements ?? 0"
              [pageSize]="100"
              [currentPage]="decisionPage()"
              [pageSizeOptions]="[50, 100]"
              (pageChange)="decisionPage.set($event.page)"
            />
          }
        </div>
      }
    </div>
  `,
})
export class BiddingRunDetailPageComponent {
  readonly runId = input.required<string>();

  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly decisionFilterValues = signal<Record<string, any>>({});
  readonly decisionPage = signal(0);

  private readonly numericRunId = computed(() => Number(this.runId()));

  readonly runQuery = injectQuery(() => ({
    queryKey: ['bidding-run', this.numericRunId()],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.getRunDetail(
          this.wsStore.currentWorkspaceId()!,
          this.numericRunId(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !isNaN(this.numericRunId()),
    staleTime: 15_000,
  }));

  readonly run = computed<BiddingRunSummary | null>(() => this.runQuery.data() ?? null);

  readonly statusCssVar = computed(() =>
    RUN_STATUS_COLOR[this.run()?.status ?? ''] ?? 'var(--status-neutral)',
  );

  readonly timingDisplay = computed(() => {
    const r = this.run();
    if (!r) return '';
    const started = formatDateTime(r.startedAt, 'full');
    const duration = this.formatDuration(r.startedAt, r.completedAt);
    if (r.status === 'RUNNING') return this.translate.instant('bidding.runs.detail.running', { time: started });
    if (r.completedAt) return `${started} · ${duration}`;
    return started;
  });

  private readonly decisionFilter = computed(() => {
    const vals = this.decisionFilterValues();
    const f: { biddingRunId: number; decisionType?: string[] } = {
      biddingRunId: this.numericRunId(),
    };
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    return f;
  });

  readonly decisionsQuery = injectQuery(() => ({
    queryKey: [
      'bidding-run-decisions',
      this.wsStore.currentWorkspaceId(),
      this.numericRunId(),
      this.decisionFilter(),
      this.decisionPage(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          {
            bidPolicyId: undefined,
            decisionType: this.decisionFilter().decisionType as any,
          },
          this.decisionPage(),
          100,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !isNaN(this.numericRunId()),
    staleTime: 30_000,
  }));

  readonly decisionRows = computed(() => this.decisionsQuery.data()?.content ?? []);

  readonly decisionFilterConfigs: FilterConfig[] = [
    {
      key: 'decisionType',
      label: 'bidding.decisions.filter.decision_type',
      type: 'multi-select',
      options: (['BID_UP', 'BID_DOWN', 'HOLD', 'PAUSE', 'RESUME', 'SET_MINIMUM', 'EMERGENCY_CUT'] as const).map(
        (value) => ({
          value,
          label: `bidding.decisions.type.${value}`,
        }),
      ),
    },
  ];

  readonly decisionColumnDefs: ColDef[] = [
    {
      headerName: this.translate.instant('bidding.decisions.col.offer'),
      field: 'marketplaceOfferId',
      width: 130,
      sortable: true,
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      cellRenderer: (params: ICellRendererParams<BidDecisionSummary>) => {
        if (!params.value) return '';
        return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline">${params.value}</span>`;
      },
      onCellClicked: (params: { data?: BidDecisionSummary }) => {
        if (params.data) {
          this.onDecisionRowClicked(params.data);
        }
      },
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.decision_type'),
      field: 'decisionType',
      width: 150,
      sortable: true,
      cellRenderer: (params: ICellRendererParams<BidDecisionSummary>) => {
        const dt = params.value as string;
        if (!dt) return '';
        const label = this.translate.instant(`bidding.decisions.type.${dt}`);
        const color = DECISION_COLOR[dt] ?? 'var(--status-neutral)';
        return renderBadge(label, color);
      },
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.current_bid'),
      field: 'currentBid',
      width: 130,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) =>
        this.formatBid(params.value),
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.target_bid'),
      field: 'targetBid',
      width: 130,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) =>
        this.formatBid(params.value),
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.explanation'),
      field: 'explanationSummary',
      minWidth: 200,
      flex: 1,
      sortable: false,
      tooltipField: 'explanationSummary',
      cellClass: 'text-[length:var(--text-sm)] text-[color:var(--text-tertiary)]',
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) => {
        if (!params.value) return '—';
        return params.value.length > 80 ? params.value.substring(0, 80) + '…' : params.value;
      },
    },
    {
      headerName: this.translate.instant('bidding.decisions.col.created_at'),
      field: 'createdAt',
      width: 140,
      sortable: true,
      valueFormatter: (params: ValueFormatterParams<BidDecisionSummary>) =>
        formatDateTime(params.value, 'full'),
    },
  ];

  readonly getRowId = (params: GetRowIdParams<BidDecisionSummary>) =>
    String(params.data.id);

  goBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'bidding', 'runs']);
  }

  onDecisionRowClicked(row: BidDecisionSummary): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'bidding', 'decisions', row.id]);
  }

  onDecisionFiltersChanged(values: Record<string, any>): void {
    this.decisionFilterValues.set(values);
    this.decisionPage.set(0);
  }

  private formatBid(value: number | null): string {
    if (value === null || value === undefined) return '—';
    return formatMoney(value / 100, 0);
  }

  private formatDuration(start: string | null, end: string | null): string {
    if (!start) return '—';
    const endMs = end ? new Date(end).getTime() : Date.now();
    const ms = endMs - new Date(start).getTime();
    if (ms < 0) return '—';
    const totalSec = Math.floor(ms / 1000);
    const secUnit = this.translate.instant('common.time.sec');
    if (totalSec < 60) return `${totalSec} ${secUnit}`;
    const min = Math.floor(totalSec / 60);
    const sec = totalSec % 60;
    const minUnit = this.translate.instant('common.time.min');
    return sec > 0 ? `${min} ${minUnit} ${sec} ${secUnit}` : `${min} ${minUnit}`;
  }
}
