import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import {
  ColDef,
  GetRowIdParams,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import {
  DecisionOutcome,
  ImpactPreviewOffer,
  ImpactPreviewResponse,
} from '@core/models';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import {
  financeColor,
  formatMoney,
  formatMoneyWithSign,
  formatPercent,
  renderBadge,
} from '@shared/utils/format.utils';

const DECISION_BADGE_COLOR: Record<DecisionOutcome, string> = {
  CHANGE: 'success',
  SKIP: 'warning',
  HOLD: 'neutral',
};

@Component({
  selector: 'dp-impact-preview-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DataGridComponent],
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-[9000] flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="impact-preview-title"
      >
        <div class="absolute inset-0 bg-[var(--bg-overlay)]" (click)="onClose()"></div>
        <div
          class="relative z-10 flex h-[80vh] w-[80vw] min-w-[960px] max-w-[1200px] flex-col overflow-hidden rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] shadow-[var(--shadow-lg)] animate-[fadeIn_150ms_ease]"
          (click)="$event.stopPropagation()"
        >
          <div
            class="flex items-center justify-between border-b border-[var(--border-default)] px-6 py-4"
          >
            <h2
              id="impact-preview-title"
              class="text-base font-semibold text-[var(--text-primary)]"
            >
              {{ 'pricing.preview.title' | translate }}
            </h2>
            <button
              type="button"
              (click)="onClose()"
              class="cursor-pointer rounded-[var(--radius-sm)] p-1.5 text-lg leading-none text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
              [attr.aria-label]="'pricing.preview.close' | translate"
            >
              ✕
            </button>
          </div>

          <div class="flex min-h-0 flex-1 flex-col gap-4 overflow-hidden px-6 py-4">
            @if (previewQuery.isError()) {
              <div
                class="flex flex-1 flex-col items-center justify-center gap-4 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] px-6 py-12"
              >
                <p class="text-center text-sm text-[var(--text-secondary)]">
                  {{ 'pricing.preview.error' | translate }}
                </p>
                <button
                  type="button"
                  (click)="previewQuery.refetch()"
                  class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
                >
                  {{ 'actions.retry' | translate }}
                </button>
              </div>
            } @else if (previewQuery.isPending()) {
              <div class="grid shrink-0 grid-cols-4 gap-3">
                @for (_ of kpiSkeletonSlots; track $index) {
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="dp-shimmer mb-2 h-3 w-24 rounded-[var(--radius-sm)]"></div>
                    <div class="dp-shimmer h-7 w-16 rounded-[var(--radius-sm)]"></div>
                  </div>
                }
              </div>
              <div
                class="min-h-0 flex-1 overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)]"
              >
                <div class="dp-shimmer h-full w-full min-h-[200px] rounded-[var(--radius-md)]"></div>
              </div>
            } @else {
              @if (summary(); as s) {
                <div class="grid shrink-0 grid-cols-4 gap-3">
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.total_offers' | translate }}
                    </div>
                    <div class="font-mono text-lg text-[var(--text-primary)]">
                      {{ s.totalOffers }}
                    </div>
                  </div>
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.eligible' | translate }}
                    </div>
                    <div class="font-mono text-lg text-[var(--text-primary)]">
                      {{ s.eligibleCount }}
                    </div>
                  </div>
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.changes' | translate }}
                    </div>
                    <div
                      class="font-mono text-lg"
                      style="color: var(--status-success)"
                    >
                      {{ s.changeCount }}
                    </div>
                  </div>
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.skips' | translate }}
                    </div>
                    <div
                      class="font-mono text-lg"
                      style="color: var(--status-warning)"
                    >
                      {{ s.skipCount }}
                    </div>
                  </div>
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.holds' | translate }}
                    </div>
                    <div
                      class="font-mono text-lg"
                      style="color: var(--status-neutral)"
                    >
                      {{ s.holdCount }}
                    </div>
                  </div>
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.avg_change' | translate }}
                    </div>
                    <div
                      class="font-mono text-lg"
                      [style.color]="financeColor(s.avgPriceChangePct)"
                    >
                      {{ formatPercent(s.avgPriceChangePct, 1, true) }}
                    </div>
                  </div>
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.max_change' | translate }}
                    </div>
                    <div
                      class="font-mono text-lg"
                      [style.color]="financeColor(s.maxPriceChangePct)"
                    >
                      {{ formatPercent(s.maxPriceChangePct, 1, true) }}
                    </div>
                  </div>
                  <div
                    class="rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3"
                  >
                    <div class="text-[var(--text-xs)] text-[var(--text-tertiary)]">
                      {{ 'pricing.preview.summary.min_margin' | translate }}
                    </div>
                    <div
                      class="font-mono text-lg"
                      [style.color]="financeColor(s.minMarginAfter)"
                    >
                      {{ formatPercent(s.minMarginAfter, 1, false) }}
                    </div>
                  </div>
                </div>
              }
              <p class="text-[var(--text-xs)] italic text-[var(--text-secondary)]">
                {{ 'pricing.preview.disclaimer' | translate }}
              </p>
              <div class="min-h-0 flex-1">
                <dp-data-grid
                  [columnDefs]="columnDefs"
                  [rowData]="offerRows()"
                  [loading]="false"
                  [pagination]="true"
                  [pageSize]="50"
                  [getRowId]="getRowId"
                  [height]="'100%'"
                />
              </div>
            }
          </div>

          <div
            class="flex items-center justify-end gap-3 border-t border-[var(--border-default)] px-6 py-3"
          >
            <button
              type="button"
              (click)="onClose()"
              class="cursor-pointer rounded-[var(--radius-md)] px-4 py-2 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'pricing.preview.close' | translate }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    @keyframes fadeIn {
      from {
        opacity: 0;
        transform: scale(0.97);
      }
      to {
        opacity: 1;
        transform: scale(1);
      }
    }
  `],
})
export class ImpactPreviewModalComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);

  readonly open = input.required<boolean>();
  readonly policyId = input.required<number>();
  readonly closed = output<void>();

  protected readonly formatPercent = formatPercent;
  protected readonly financeColor = financeColor;

  /** Template @for track — 8 KPI skeleton cards */
  readonly kpiSkeletonSlots = [0, 1, 2, 3, 4, 5, 6, 7];

  readonly previewQuery = injectQuery<ImpactPreviewResponse>(() => ({
    queryKey: [
      'impact-preview',
      this.wsStore.currentWorkspaceId(),
      this.policyId(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.simulateImpact(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
        ),
      ),
    enabled: this.open() && !!this.wsStore.currentWorkspaceId(),
    staleTime: 0,
  }));

  readonly summary = computed(() => this.previewQuery.data()?.summary);

  readonly offerRows = computed(
    () => this.previewQuery.data()?.offers.content ?? [],
  );

  readonly columnDefs: ColDef[] = [
    {
      headerName: this.translate.instant('pricing.preview.col.offer'),
      field: 'offerName',
      minWidth: 250,
      flex: 1,
      sortable: true,
    },
    {
      headerName: this.translate.instant('pricing.preview.col.sku'),
      field: 'sellerSku',
      width: 120,
      sortable: true,
      cellClass: 'font-mono',
    },
    {
      headerName: this.translate.instant('pricing.preview.col.current_price'),
      field: 'currentPrice',
      width: 120,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: ValueFormatterParams<ImpactPreviewOffer>) =>
        formatMoney(params.value, 0),
    },
    {
      headerName: this.translate.instant('pricing.preview.col.target_price'),
      field: 'targetPrice',
      width: 120,
      sortable: true,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: ValueFormatterParams<ImpactPreviewOffer>) =>
        formatMoney(params.value, 0),
    },
    {
      headerName: this.translate.instant('pricing.preview.col.change_pct'),
      field: 'changePct',
      width: 80,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellRenderer: (params: ICellRendererParams<ImpactPreviewOffer>) => {
        const v = params.value as number | null | undefined;
        if (v === null || v === undefined) {
          return '—';
        }
        const abs = Math.abs(v).toFixed(1).replace('.', ',');
        const color = financeColor(v);
        const arrow = v > 0 ? '↑' : v < 0 ? '↓' : '→';
        return `<span style="color: ${color}">${arrow} ${abs}%</span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.preview.col.change_amount'),
      field: 'changeAmount',
      width: 100,
      sortable: true,
      cellClass: 'font-mono text-right',
      cellRenderer: (params: ICellRendererParams<ImpactPreviewOffer>) => {
        const v = params.value as number | null | undefined;
        if (v === null || v === undefined) {
          return '—';
        }
        const color = financeColor(v);
        return `<span style="color: ${color}">${formatMoneyWithSign(v, 0)}</span>`;
      },
    },
    {
      headerName: this.translate.instant('pricing.preview.col.decision'),
      field: 'decisionType',
      width: 100,
      sortable: true,
      cellRenderer: (params: ICellRendererParams<ImpactPreviewOffer>) => {
        const val = params.value as DecisionOutcome;
        const label = this.translate.instant(`pricing.decisions.type.${val}`);
        const color = DECISION_BADGE_COLOR[val] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return renderBadge(label, cssVar);
      },
    },
    {
      headerName: this.translate.instant('pricing.preview.col.reason'),
      field: 'skipReason',
      minWidth: 200,
      flex: 1,
      sortable: false,
      valueFormatter: (params: ValueFormatterParams<ImpactPreviewOffer>) => {
        const key = params.value as string | null | undefined;
        if (!key) {
          return '—';
        }
        return this.translate.instant(key);
      },
    },
  ];

  readonly getRowId = (params: GetRowIdParams<ImpactPreviewOffer>) =>
    `${params.data.sellerSku}-${params.data.offerName}`;

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.open()) {
      this.onClose();
    }
  }

  onClose(): void {
    this.closed.emit();
  }
}
