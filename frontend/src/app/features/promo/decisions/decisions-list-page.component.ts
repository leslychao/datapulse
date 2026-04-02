import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { CheckCircle, XCircle, Clock } from 'lucide-angular';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatDateTime } from '@shared/utils/format.utils';
import {
  ParticipationMode,
  PromoDecisionFilter,
  PromoDecisionType,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';

const DECISION_LABEL: Record<PromoDecisionType, string> = {
  PARTICIPATE: 'Участвовать',
  DECLINE: 'Отказать',
  PENDING_REVIEW: 'На проверку',
};

const DECISION_COLOR: Record<PromoDecisionType, string> = {
  PARTICIPATE: 'success',
  DECLINE: 'neutral',
  PENDING_REVIEW: 'warning',
};

const MODE_LABEL: Record<ParticipationMode, string> = {
  RECOMMENDATION: 'Рекомендация',
  SEMI_AUTO: 'Полу-авто',
  FULL_AUTO: 'Полный авто',
  SIMULATED: 'Симуляция',
};

const MP_BADGE: Record<string, { bg: string; label: string }> = {
  WB: { bg: '#CB11AB', label: 'WB' },
  OZON: { bg: '#005BFF', label: 'Ozon' },
};

@Component({
  selector: 'dp-decisions-list-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    KpiCardComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'promo.decisions.title' | translate }}
        </h2>
      </div>

      <div class="flex flex-wrap gap-3 px-4 pt-3">
        <dp-kpi-card
          [label]="'promo.decisions.kpi.participate' | translate"
          [value]="kpiParticipate()"
          [icon]="CheckCircleIcon"
          accent="success"
          [loading]="decisionsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.decisions.kpi.decline' | translate"
          [value]="kpiDecline()"
          [icon]="XCircleIcon"
          accent="neutral"
          [loading]="decisionsQuery.isPending()"
        />
        <dp-kpi-card
          [label]="'promo.decisions.kpi.pending_review' | translate"
          [value]="kpiPendingReview()"
          [icon]="ClockIcon"
          accent="warning"
          [loading]="decisionsQuery.isPending()"
        />
      </div>

      <div class="px-6 pt-2">
        <dp-filter-bar
          [filters]="filterConfigs"
          [values]="filterValues()"
          (filtersChanged)="onFiltersChanged($event)"
        />
      </div>

      <div class="flex-1 px-4 py-2">
        @if (decisionsQuery.isError()) {
          <dp-empty-state
            [message]="'promo.decisions.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="decisionsQuery.refetch()"
          />
        } @else if (!decisionsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="hasActiveFilters()
              ? ('promo.decisions.empty_filtered' | translate)
              : ('promo.decisions.empty' | translate)"
            [actionLabel]="hasActiveFilters()
              ? ('filter_bar.reset_all' | translate)
              : ''"
            (action)="onFiltersChanged({})"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="decisionsQuery.isPending()"
            [pagination]="true"
            [pageSize]="50"
            [getRowId]="getRowId"
            [height]="'100%'"
          />
        }
      </div>
    </div>
  `,
})
export class DecisionsListPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  protected readonly CheckCircleIcon = CheckCircle;
  protected readonly XCircleIcon = XCircle;
  protected readonly ClockIcon = Clock;

  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'decisionType',
      label: 'grid.filter.decision',
      type: 'multi-select',
      options: (Object.keys(DECISION_LABEL) as PromoDecisionType[]).map((value) => ({
        value,
        label: `promo.decision_type.${value}`,
      })),
    },
    { key: 'search', label: 'promo.filter.search_product', type: 'text' },
  ];

  readonly columnDefs = [
    {
      headerName: 'Кампания',
      field: 'campaignName',
      minWidth: 200,
      sortable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        return `<span class="font-medium" title="${params.data.campaignName}">${params.data.campaignName}</span>`;
      },
    },
    {
      headerName: 'Маркетплейс',
      field: 'sourcePlatform',
      width: 100,
      cellClass: 'text-center',
      cellRenderer: (params: any) => {
        const mp = MP_BADGE[params.value];
        if (!mp) return params.value ?? '';
        return `<span class="inline-flex items-center rounded px-2 py-0.5 text-[11px] font-bold text-white" style="background:${mp.bg}">${mp.label}</span>`;
      },
    },
    {
      headerName: 'Товар',
      field: 'productName',
      minWidth: 230,
      sortable: true,
    },
    {
      headerName: 'SKU',
      field: 'marketplaceSku',
      width: 120,
      cellClass: 'font-mono',
    },
    {
      headerName: 'Решение',
      field: 'decisionType',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => {
        const val = params.value as PromoDecisionType;
        if (!val) return '';
        const label = DECISION_LABEL[val] ?? val;
        const color = DECISION_COLOR[val] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Режим',
      field: 'participationMode',
      width: 120,
      valueFormatter: (params: any) => MODE_LABEL[params.value as ParticipationMode] ?? params.value,
    },
    {
      headerName: 'Целевая промо-цена',
      field: 'targetPromoPrice',
      width: 130,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) =>
        params.value != null ? params.value.toLocaleString('ru-RU') + ' ₽' : '—',
    },
    {
      headerName: 'Объяснение',
      field: 'explanationSummary',
      minWidth: 250,
      cellRenderer: (params: any) => {
        if (!params.value) return '—';
        const truncated = params.value.length > 80
          ? params.value.substring(0, 80) + '…'
          : params.value;
        return `<span title="${params.value}" class="text-[var(--text-secondary)]">${truncated}</span>`;
      },
    },
    {
      headerName: 'Принято',
      field: 'decidedByName',
      width: 130,
      valueFormatter: (params: any) => params.value ?? 'Автоматически',
    },
    {
      headerName: 'Политика',
      field: 'policyName',
      width: 160,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        const name = params.data.policyName ?? '';
        const ver = params.data.policyVersion != null ? ` v${params.data.policyVersion}` : '';
        return `${name}${ver}`;
      },
    },
    {
      headerName: 'Дата',
      field: 'createdAt',
      width: 130,
      sortable: true,
      sort: 'desc' as const,
      valueFormatter: (params: any) => formatDateTime(params.value, 'full'),
    },
  ];

  private readonly filter = computed<PromoDecisionFilter>(() => {
    const vals = this.filterValues();
    const f: PromoDecisionFilter = {};
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  readonly decisionsQuery = injectQuery(() => ({
    queryKey: [
      'promo-decisions',
      this.wsStore.currentWorkspaceId(),
      this.filter(),
      this.currentPage(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listDecisions(
          this.wsStore.currentWorkspaceId()!,
          this.filter(),
          this.currentPage(),
          50,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.decisionsQuery.data()?.content ?? []);

  readonly kpiParticipate = computed(() =>
    this.rows().filter((d) => d.decisionType === 'PARTICIPATE').length,
  );
  readonly kpiDecline = computed(() =>
    this.rows().filter((d) => d.decisionType === 'DECLINE').length,
  );
  readonly kpiPendingReview = computed(() =>
    this.rows().filter((d) => d.decisionType === 'PENDING_REVIEW').length,
  );

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined && (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly getRowId = (params: any) => String(params.data.id);

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

}
