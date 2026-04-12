import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { BidDecisionDetail } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ExplanationBlockComponent } from '@shared/components/explanation-block.component';
import { LoadingSkeletonComponent } from '@shared/components/loading-skeleton.component';
import { formatMoney, formatDateTime, renderBadge } from '@shared/utils/format.utils';

const DECISION_TYPE_COLOR: Record<string, string> = {
  BID_UP: 'success',
  BID_DOWN: 'error',
  HOLD: 'neutral',
  PAUSE: 'warning',
  RESUME: 'info',
  SET_MINIMUM: 'info',
  EMERGENCY_CUT: 'error',
};

@Component({
  selector: 'dp-bid-decision-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-1 flex-col min-h-0 overflow-y-auto' },
  imports: [
    TranslatePipe,
    EmptyStateComponent,
    ExplanationBlockComponent,
    LoadingSkeletonComponent,
  ],
  template: `
    <!-- Header -->
    <div class="shrink-0 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-3">
      <div class="flex items-center gap-3">
        <button
          type="button"
          (click)="navigateBack()"
          class="cursor-pointer rounded-[var(--radius-sm)] px-2 py-1 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        >
          {{ 'bidding.decisions.detail.back' | translate }}
        </button>
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'bidding.decisions.detail.title' | translate }} #{{ decisionId() }}
        </h2>
        @if (decision()) {
          <span
            class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
            [style.background-color]="'color-mix(in srgb, ' + decisionTypeCssVar() + ' 12%, transparent)'"
            [style.color]="decisionTypeCssVar()"
          >
            <span class="inline-block h-1.5 w-1.5 rounded-full" [style.background-color]="decisionTypeCssVar()"></span>
            {{ ('bidding.decisions.type.' + decision()!.decisionType) | translate }}
          </span>
          <span class="text-sm text-[var(--text-tertiary)]">{{ formatCreatedAt(decision()!.createdAt) }}</span>
        }
      </div>
    </div>

    <div class="flex flex-1 flex-col gap-6 px-4 py-4">
      @if (decisionQuery.isPending()) {
        <div class="flex flex-col gap-4">
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            @for (i of [0, 1, 2, 3]; track i) {
              <div class="rounded-[var(--radius-md)] border border-[var(--border-default)] p-3">
                <div class="dp-shimmer mb-2 h-3 w-24 rounded-[var(--radius-sm)]"></div>
                <div class="dp-shimmer h-4 w-full rounded-[var(--radius-sm)]"></div>
              </div>
            }
          </div>
          <dp-loading-skeleton [type]="'card'" [lines]="1" />
          <dp-loading-skeleton [type]="'table-row'" [lines]="5" />
        </div>
      } @else if (decisionQuery.isError()) {
        <dp-empty-state
          [message]="'bidding.decisions.detail.not_found' | translate"
          [actionLabel]="'actions.retry' | translate"
          (action)="decisionQuery.refetch()"
        />
      } @else {
        @if (decision(); as d) {
        <!-- Meta -->
        <section class="flex flex-col gap-3">
          <h3 class="text-sm font-semibold text-[var(--text-primary)]">
            {{ 'bidding.decisions.detail.meta' | translate }}
          </h3>
          <div class="grid grid-cols-1 gap-x-6 gap-y-2 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-4 sm:grid-cols-2">
            <div class="flex flex-col gap-0.5">
              <span class="text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                {{ 'bidding.decisions.col.offer' | translate }}
              </span>
              <span class="font-mono text-sm text-[var(--text-primary)]">{{ d.marketplaceOfferId }}</span>
            </div>
            <div class="flex flex-col gap-0.5">
              <span class="text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                {{ 'bidding.decisions.col.strategy' | translate }}
              </span>
              <span class="text-sm text-[var(--text-primary)]">
                {{ ('bidding.policies.strategy.' + d.strategyType) | translate }}
              </span>
            </div>
            <div class="flex flex-col gap-0.5">
              <span class="text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                {{ 'bidding.decisions.col.mode' | translate }}
              </span>
              <span class="text-sm text-[var(--text-primary)]">
                {{ ('bidding.policies.mode.' + d.executionMode) | translate }}
              </span>
            </div>
            <div class="flex flex-col gap-0.5">
              <span class="text-[length:var(--text-sm)] text-[var(--text-tertiary)]">
                {{ 'bidding.decisions.detail.run_link' | translate }}
              </span>
              <button
                type="button"
                class="w-fit cursor-pointer font-mono text-sm text-[var(--accent-primary)] transition-colors hover:underline"
                (click)="navigateToRun(d.biddingRunId)"
              >
                #{{ d.biddingRunId }}
              </button>
            </div>
          </div>
        </section>

        <!-- Bid change block -->
        <section class="flex flex-col gap-3">
          <h3 class="text-sm font-semibold text-[var(--text-primary)]">
            {{ 'bidding.decisions.detail.bid_change' | translate }}
          </h3>
          <div class="flex items-center gap-4 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-4">
            <div class="flex flex-col gap-0.5">
              <span class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">{{ 'bidding.decisions.col.current_bid' | translate }}</span>
              <span class="font-mono text-lg font-semibold text-[var(--text-primary)]">{{ formatBid(d.currentBid) }}</span>
            </div>
            <span class="text-xl text-[var(--text-tertiary)]">→</span>
            <div class="flex flex-col gap-0.5">
              <span class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">{{ 'bidding.decisions.col.target_bid' | translate }}</span>
              <span class="font-mono text-lg font-semibold" [class]="bidChangeClass()">{{ formatBid(d.targetBid) }}</span>
            </div>
            @if (bidDelta() !== null) {
              <div class="ml-4 flex flex-col gap-0.5">
                <span class="text-[length:var(--text-xs)] text-[var(--text-tertiary)]">Δ</span>
                <span class="font-mono text-sm" [class]="bidChangeClass()">{{ bidDeltaDisplay() }}</span>
              </div>
            }
          </div>
        </section>

        <!-- Explanation -->
        @if (d.explanationSummary) {
          <section class="flex flex-col gap-3">
            <h3 class="text-sm font-semibold text-[var(--text-primary)]">
              {{ 'bidding.decisions.detail.explanation' | translate }}
            </h3>
            <dp-explanation-block [text]="d.explanationSummary" />
          </section>
        }

        <!-- Signal snapshot table -->
        @if (signalRows().length > 0) {
          <section class="flex flex-col gap-3">
            <h3 class="text-sm font-semibold text-[var(--text-primary)]">
              {{ 'bidding.decisions.detail.signals' | translate }}
            </h3>
            <div class="dp-table-wrap overflow-x-auto">
              <table class="dp-table dp-table-compact">
                <thead>
                  <tr>
                    <th>{{ 'bidding.decisions.detail.signal_key' | translate }}</th>
                    <th>{{ 'bidding.decisions.detail.signal_value' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of signalRows(); track row.key) {
                    <tr>
                      <td class="text-[var(--text-primary)]">{{ row.label }}</td>
                      <td class="font-mono text-[var(--text-primary)]">{{ row.value }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }

        <!-- Guards table -->
        @if (guardsRows().length > 0) {
          <section class="flex flex-col gap-3">
            <h3 class="text-sm font-semibold text-[var(--text-primary)]">
              {{ 'bidding.decisions.detail.guards' | translate }}
            </h3>
            <div class="dp-table-wrap overflow-x-auto">
              <table class="dp-table dp-table-compact">
                <thead>
                  <tr>
                    <th class="w-12">#</th>
                    <th>{{ 'bidding.decisions.detail.guard_name' | translate }}</th>
                    <th>{{ 'bidding.decisions.detail.guard_result' | translate }}</th>
                    <th class="min-w-[200px]">{{ 'bidding.decisions.detail.guard_message' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (g of guardsRows(); track $index) {
                    <tr>
                      <td class="text-[var(--text-tertiary)]">{{ $index + 1 }}</td>
                      <td class="text-[var(--text-primary)]">{{ g.guardName }}</td>
                      <td>
                        @if (g.allowed) {
                          <span class="inline-flex items-center gap-1 text-[var(--status-success)]">
                            <span aria-hidden="true">✓</span>
                            <span>{{ 'bidding.decisions.detail.guard_allowed' | translate }}</span>
                          </span>
                        } @else {
                          <span class="inline-flex items-center gap-1 text-[var(--status-error)]">
                            <span aria-hidden="true">✗</span>
                            <span>{{ 'bidding.decisions.detail.guard_blocked' | translate }}</span>
                          </span>
                        }
                      </td>
                      <td class="text-[var(--text-secondary)]">
                        {{ guardMessage(g) }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }
        }
      }
    </div>
  `,
})
export class BidDecisionDetailPageComponent {
  readonly decisionId = input.required<string>();

  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  private readonly numericDecisionId = computed(() => Number(this.decisionId()));

  readonly decisionQuery = injectQuery(() => ({
    queryKey: ['bid-decision-detail', this.wsStore.currentWorkspaceId(), this.numericDecisionId()],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.getDecision(
          this.wsStore.currentWorkspaceId()!,
          this.numericDecisionId(),
        ),
      ).catch((err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.toast.error(
            this.translate.instant('bidding.decisions.detail.not_found'),
          );
          const wsId = this.wsStore.currentWorkspaceId();
          if (wsId) {
            void this.router.navigate(['/workspace', wsId, 'bidding', 'decisions']);
          }
        }
        throw err;
      }),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly decision = computed<BidDecisionDetail | null>(
    () => this.decisionQuery.data() ?? null,
  );

  readonly decisionTypeCssVar = computed(() => {
    const dt = this.decision()?.decisionType ?? '';
    const color = DECISION_TYPE_COLOR[dt] ?? 'neutral';
    return `var(--status-${color})`;
  });

  readonly bidDelta = computed(() => {
    const d = this.decision();
    if (!d || d.currentBid == null || d.targetBid == null) return null;
    return d.targetBid - d.currentBid;
  });

  readonly bidDeltaDisplay = computed(() => {
    const delta = this.bidDelta();
    const d = this.decision();
    if (delta === null || !d) return '';
    const kopecks = Math.abs(delta);
    const rubles = formatMoney(kopecks / 100, 0);
    const pct = d.currentBid && d.currentBid > 0
      ? ((delta / d.currentBid) * 100).toFixed(1).replace('.', ',')
      : '0';
    const sign = delta > 0 ? '+' : delta < 0 ? '−' : '';
    return `${sign}${rubles} (${sign}${Math.abs(Number(pct.replace(',', '.')))}%)`;
  });

  readonly bidChangeClass = computed(() => {
    const delta = this.bidDelta();
    if (delta === null || delta === 0) return 'text-[var(--text-primary)]';
    return delta > 0 ? 'text-[var(--finance-positive)]' : 'text-[var(--finance-negative)]';
  });

  readonly signalRows = computed(() => {
    const snap = this.decision()?.signalSnapshot;
    if (!snap) return [];
    return Object.keys(snap).sort().map((key) => ({
      key,
      label: this.signalKeyLabel(key),
      value: this.formatUnknownValue(snap[key]),
    }));
  });

  readonly guardsRows = computed(() => {
    const guards = this.decision()?.guardsApplied;
    if (!guards) return [];
    if (Array.isArray(guards)) return guards;
    return Object.entries(guards).map(([guardName, v]) => ({
      guardName,
      allowed: typeof v === 'object' && v !== null ? (v as any).allowed ?? true : true,
      messageKey: typeof v === 'object' && v !== null ? (v as any).messageKey ?? null : null,
      args: typeof v === 'object' && v !== null ? (v as any).args ?? {} : {},
    }));
  });

  navigateBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) return;
    void this.router.navigate(['/workspace', wsId, 'bidding', 'decisions']);
  }

  navigateToRun(runId: number): void {
    const wsId = this.wsStore.currentWorkspaceId();
    if (!wsId) return;
    void this.router.navigate(['/workspace', wsId, 'bidding', 'runs', runId]);
  }

  formatBid(value: number | null | undefined): string {
    if (value === null || value === undefined) return '—';
    return formatMoney(value / 100, 0);
  }

  formatCreatedAt(iso: string | null | undefined): string {
    return formatDateTime(iso, 'full');
  }

  guardMessage(g: any): string {
    if (!g.messageKey) return '—';
    return this.translate.instant(g.messageKey, g.args ?? {});
  }

  private signalKeyLabel(key: string): string {
    const tk = `bidding.decisions.signal.${key}`;
    const t = this.translate.instant(tk);
    return t === tk ? key : t;
  }

  private formatUnknownValue(value: unknown): string {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  }
}
