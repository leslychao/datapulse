import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom, startWith } from 'rxjs';
import {
  LucideAngularModule,
  Play,
  CheckCircle,
  XCircle,
  PauseCircle,
  ListTodo,
} from 'lucide-angular';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { BiddingRunSummary, BidPolicySummary } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { FormModalComponent } from '@shared/components/form-modal.component';
import { formatRelativeTime, renderBadge } from '@shared/utils/format.utils';
import { createListPageState } from '@shared/utils/list-page-state';

const RUN_STATUS_COLOR: Record<string, string> = {
  RUNNING: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
  PAUSED: 'warning',
};

@Component({
  selector: 'dp-bidding-runs-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    DataGridComponent,
    EmptyStateComponent,
    KpiCardComponent,
    FormModalComponent,
  ],
  host: { class: 'flex flex-1 flex-col min-h-0' },
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-sm font-semibold text-[var(--text-primary)]">
          {{ 'bidding.runs.title' | translate }}
        </h2>
        @if (rbac.canWritePolicies()) {
          <button
            (click)="showTriggerModal.set(true)"
            class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            {{ 'bidding.runs.trigger' | translate }}
          </button>
        }
      </div>

      <!-- KPI cards -->
      <div class="flex flex-wrap gap-3 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-3">
        <dp-kpi-card
          [label]="'bidding.runs.kpi.total' | translate"
          [value]="kpi().total"
          [icon]="kpiIcons.ListTodo"
          accent="neutral"
          [loading]="runsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'bidding.runs.kpi.completed' | translate"
          [value]="kpi().completed"
          [icon]="kpiIcons.CheckCircle"
          accent="success"
          [loading]="runsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'bidding.runs.kpi.failed' | translate"
          [value]="kpi().failed"
          [icon]="kpiIcons.XCircle"
          accent="error"
          [loading]="runsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'bidding.runs.kpi.paused' | translate"
          [value]="kpi().paused"
          [icon]="kpiIcons.PauseCircle"
          accent="warning"
          [loading]="runsQuery.isPending()"
        />
      </div>

      <div class="flex-1 px-4 py-2">
        @if (runsQuery.isError()) {
          <dp-empty-state
            [message]="'bidding.runs.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="runsQuery.refetch()"
          />
        } @else if (!runsQuery.isPending() && rows().length === 0) {
          <dp-empty-state [message]="'bidding.runs.empty' | translate" />
        } @else {
          <dp-data-grid
            viewStateKey="bidding:runs"
            [columnDefs]="columnDefs()"
            [rowData]="rows()"
            [loading]="runsQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
            [initialSortModel]="listState.initialSortModel()"
            (sortChanged)="listState.onSortChanged($event)"
            (rowClicked)="onRowClicked($event)"
          />
        }
      </div>
    </div>

    <dp-form-modal
      [title]="'bidding.runs.trigger_title' | translate"
      [isOpen]="showTriggerModal()"
      [submitLabel]="'bidding.runs.trigger' | translate"
      [cancelLabel]="'common.cancel' | translate"
      [submitDisabled]="!triggerPolicyId()"
      [isPending]="triggerMutation.isPending()"
      (submit)="executeTrigger()"
      (close)="showTriggerModal.set(false)"
    >
      <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
        {{ 'bidding.runs.trigger_policy_label' | translate }}
      </label>
      <select
        [ngModel]="triggerPolicyId()"
        (ngModelChange)="triggerPolicyId.set($event)"
        class="h-9 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-[var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
      >
        <option [ngValue]="null" disabled>
          {{ 'bidding.runs.trigger_policy_placeholder' | translate }}
        </option>
        @for (p of activePolicies(); track p.id) {
          <option [ngValue]="p.id">{{ p.name }}</option>
        }
      </select>
    </dp-form-modal>
  `,
})
export class BiddingRunsListPageComponent {
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly kpiIcons = { ListTodo, CheckCircle, XCircle, PauseCircle };

  readonly listState = createListPageState({
    pageKey: 'bidding:runs',
    defaultSort: { column: '', direction: 'desc' },
  });

  readonly showTriggerModal = signal(false);
  readonly triggerPolicyId = signal<number | null>(null);

  private readonly translationChange = toSignal(
    this.translate.onTranslationChange.pipe(startWith(null)),
  );

  readonly columnDefs = computed(() => {
    this.translationChange();
    return [
      {
        headerName: this.translate.instant('bidding.runs.col.id'),
        field: 'id',
        width: 80,
        sortable: true,
        cellClass: 'font-mono',
      },
      {
        headerName: this.translate.instant('bidding.runs.col.status'),
        field: 'status',
        width: 130,
        sortable: true,
        cellRenderer: (params: any) => {
          const st = params.value as string;
          const label = this.translate.instant('bidding.runs.status.' + st);
          return renderBadge(label, `var(--status-${RUN_STATUS_COLOR[st] ?? 'neutral'})`, st === 'RUNNING');
        },
      },
      {
        headerName: this.translate.instant('bidding.runs.col.policy'),
        field: 'bidPolicyId',
        width: 100,
        sortable: true,
        cellClass: 'font-mono',
      },
      {
        headerName: this.translate.instant('bidding.runs.col.eligible'),
        field: 'totalEligible',
        width: 110,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        headerName: this.translate.instant('bidding.runs.col.bid_up'),
        field: 'totalBidUp',
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        headerName: this.translate.instant('bidding.runs.col.bid_down'),
        field: 'totalBidDown',
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        headerName: this.translate.instant('bidding.runs.col.hold'),
        field: 'totalHold',
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        headerName: this.translate.instant('bidding.runs.col.pause'),
        field: 'totalPause',
        width: 100,
        sortable: true,
        cellClass: 'font-mono text-right',
      },
      {
        headerName: this.translate.instant('bidding.runs.col.started_at'),
        field: 'startedAt',
        width: 130,
        sortable: true,
        valueFormatter: (params: any) => formatRelativeTime(params.value),
      },
      {
        headerName: this.translate.instant('bidding.runs.col.completed_at'),
        field: 'completedAt',
        width: 130,
        sortable: true,
        valueFormatter: (params: any) =>
          params.value ? formatRelativeTime(params.value) : '—',
      },
    ];
  });

  readonly runsQuery = injectQuery(() => ({
    queryKey: ['bid-runs', this.wsStore.currentWorkspaceId(), this.listState.queryDeps()],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listRuns(this.wsStore.currentWorkspaceId()!),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.runsQuery.data()?.content ?? []);

  readonly kpi = computed(() => {
    const all = this.rows();
    return {
      total: all.length,
      completed: all.filter((r) => r.status === 'COMPLETED').length,
      failed: all.filter((r) => r.status === 'FAILED').length,
      paused: all.filter((r) => r.status === 'PAUSED').length,
    };
  });

  private readonly policiesQuery = injectQuery(() => ({
    queryKey: ['bid-policies-for-trigger', this.wsStore.currentWorkspaceId()],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listPolicies(
          this.wsStore.currentWorkspaceId()!,
          { status: ['ACTIVE'] },
          0,
          100,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 60_000,
  }));

  readonly activePolicies = computed(() =>
    this.policiesQuery.data()?.content ?? [],
  );

  readonly triggerMutation = injectMutation(() => ({
    mutationFn: (policyId: number) =>
      lastValueFrom(
        this.biddingApi.triggerRun(this.wsStore.currentWorkspaceId()!, policyId),
      ),
    onSuccess: () => {
      this.showTriggerModal.set(false);
      this.triggerPolicyId.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['bid-runs'] });
      this.toast.success(this.translate.instant('bidding.runs.triggered'));
    },
    onError: () => {
      this.showTriggerModal.set(false);
      this.toast.error(this.translate.instant('bidding.runs.trigger_error'));
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  onRowClicked(event: { data?: BiddingRunSummary }): void {
    if (!event.data) return;
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'bidding', 'runs', event.data.id]);
  }

  executeTrigger(): void {
    const pId = this.triggerPolicyId();
    if (pId) this.triggerMutation.mutate(pId);
  }
}
