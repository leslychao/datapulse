import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PromoApiService } from '@core/api/promo-api.service';
import { formatMoney, formatDateTime } from '@shared/utils/format.utils';
import {
  CampaignStatus,
  EvaluationResult,
  ParticipationStatus,
  PromoActionStatus,
  PromoDecisionType,
  PromoProductFilter,
  PromoProductSummary,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { FilterBarComponent, FilterConfig } from '@shared/components/filter-bar/filter-bar.component';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { KpiCardComponent } from '@shared/components/kpi-card.component';
import { StatusBadgeComponent, StatusColor } from '@shared/components/status-badge.component';

const CAMPAIGN_STATUS_COLOR: Record<CampaignStatus, StatusColor> = {
  UPCOMING: 'info',
  ACTIVE: 'success',
  FROZEN: 'warning',
  ENDED: 'neutral',
  CANCELLED: 'neutral',
};

const CAMPAIGN_STATUS_LABEL: Record<CampaignStatus, string> = {
  UPCOMING: 'Предстоящая',
  ACTIVE: 'Активна',
  FROZEN: 'Заморожена',
  ENDED: 'Завершена',
  CANCELLED: 'Отменена',
};

const PARTICIPATION_LABEL: Record<ParticipationStatus, string> = {
  ELIGIBLE: 'Доступен',
  PARTICIPATING: 'Участвует',
  DECLINED: 'Отклонён',
  REMOVED: 'Удалён',
  BANNED: 'Заблокирован',
  AUTO_DECLINED: 'Авто-отклонён',
};

const PARTICIPATION_COLOR: Record<ParticipationStatus, string> = {
  ELIGIBLE: 'info',
  PARTICIPATING: 'success',
  DECLINED: 'neutral',
  REMOVED: 'neutral',
  BANNED: 'error',
  AUTO_DECLINED: 'neutral',
};

const EVAL_LABEL: Record<EvaluationResult, string> = {
  PROFITABLE: 'Прибыльно',
  MARGINAL: 'Пограничный',
  UNPROFITABLE: 'Убыточно',
  INSUFFICIENT_STOCK: 'Мало остатков',
  INSUFFICIENT_DATA: 'Нет данных',
};

const EVAL_COLOR: Record<EvaluationResult, string> = {
  PROFITABLE: 'success',
  MARGINAL: 'warning',
  UNPROFITABLE: 'error',
  INSUFFICIENT_STOCK: 'warning',
  INSUFFICIENT_DATA: 'warning',
};

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

const ACTION_LABEL: Record<PromoActionStatus, string> = {
  PENDING_APPROVAL: 'Ожидает одобрения',
  APPROVED: 'Одобрено',
  EXECUTING: 'Выполняется',
  SUCCEEDED: 'Выполнено',
  FAILED: 'Ошибка',
  EXPIRED: 'Истекло',
  CANCELLED: 'Отменено',
};

const ACTION_COLOR: Record<PromoActionStatus, string> = {
  PENDING_APPROVAL: 'warning',
  APPROVED: 'info',
  EXECUTING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
  EXPIRED: 'neutral',
  CANCELLED: 'neutral',
};

const MP_BADGE: Record<string, { bg: string; label: string }> = {
  WB: { bg: '#CB11AB', label: 'WB' },
  OZON: { bg: '#005BFF', label: 'Ozon' },
};

@Component({
  selector: 'dp-campaign-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FilterBarComponent,
    DataGridComponent,
    EmptyStateComponent,
    KpiCardComponent,
    StatusBadgeComponent,
  ],
  templateUrl: './campaign-detail-page.component.html',
})
export class CampaignDetailPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);

  readonly campaignId = input.required<string>();

  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'participationStatus',
      label: 'Статус участия',
      type: 'multi-select',
      options: Object.entries(PARTICIPATION_LABEL).map(([value, label]) => ({ value, label })),
    },
    {
      key: 'evaluationResult',
      label: 'Оценка',
      type: 'multi-select',
      options: Object.entries(EVAL_LABEL).map(([value, label]) => ({ value, label })),
    },
    {
      key: 'decisionType',
      label: 'Решение',
      type: 'multi-select',
      options: Object.entries(DECISION_LABEL).map(([value, label]) => ({ value, label })),
    },
    { key: 'search', label: 'Поиск по товару', type: 'text' },
  ];

  readonly columnDefs = [
    {
      headerName: 'Товар',
      field: 'productName',
      minWidth: 280,
      pinned: 'left' as const,
      sortable: true,
      cellRenderer: (params: any) =>
        params.data
          ? `<span class="font-medium" title="${params.data.productName}">${params.data.productName}</span>`
          : '',
    },
    {
      headerName: 'SKU',
      field: 'marketplaceSku',
      width: 120,
      cellClass: 'font-mono',
    },
    {
      headerName: 'Промо-цена',
      field: 'requiredPrice',
      width: 110,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) => formatMoney(params.value),
    },
    {
      headerName: 'Текущая цена',
      field: 'currentPrice',
      width: 110,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) => formatMoney(params.value),
    },
    {
      headerName: 'Скидка',
      field: 'discountPct',
      width: 80,
      cellClass: 'font-mono text-right',
      sortable: true,
      cellRenderer: (params: any) => {
        if (params.value == null) return '—';
        return `<span style="color: var(--finance-negative)">${params.value.toFixed(1).replace('.', ',')}%</span>`;
      },
    },
    {
      headerName: 'Маржа',
      field: 'marginAtPromoPrice',
      width: 100,
      cellClass: 'font-mono text-right',
      sortable: true,
      cellRenderer: (params: any) => {
        if (params.value == null) return '—';
        const color = params.value >= 0 ? 'var(--finance-positive)' : 'var(--finance-negative)';
        return `<span style="color:${color}">${params.value.toFixed(1).replace('.', ',')}%</span>`;
      },
    },
    {
      headerName: 'Остатки',
      field: 'stockAvailable',
      width: 80,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) => this.formatNumber(params.value),
    },
    {
      headerName: 'Оценка',
      field: 'evaluationResult',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => this.badgeCell(params.value, EVAL_LABEL, EVAL_COLOR),
    },
    {
      headerName: 'Решение',
      field: 'decisionType',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => this.badgeCell(params.value, DECISION_LABEL, DECISION_COLOR),
    },
    {
      headerName: 'Статус участия',
      field: 'participationStatus',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => this.badgeCell(params.value, PARTICIPATION_LABEL, PARTICIPATION_COLOR),
    },
    {
      headerName: 'Действие',
      field: 'actionStatus',
      width: 140,
      cellRenderer: (params: any) => this.badgeCell(params.value, ACTION_LABEL, ACTION_COLOR),
    },
    {
      headerName: '',
      field: 'actions',
      width: 160,
      sortable: false,
      suppressMovable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        const p = params.data as PromoProductSummary;
        const frozen = this.isCampaignFrozenOrEnded();
        if (frozen) return '';

        let btns = '';
        if (
          (p.participationStatus === 'ELIGIBLE' && !p.actionId) ||
          p.participationStatus === 'DECLINED' ||
          p.participationStatus === 'AUTO_DECLINED'
        ) {
          btns += `<button class="action-btn" data-action="participate" title="Участвовать">✓</button>`;
        }
        if (p.participationStatus === 'ELIGIBLE' && !p.actionId) {
          btns += `<button class="action-btn" data-action="decline" title="Отклонить">✗</button>`;
        }
        if (p.actionStatus === 'PENDING_APPROVAL') {
          btns += `<button class="action-btn" data-action="approve" title="Одобрить">✓</button>`;
          btns += `<button class="action-btn" data-action="reject" title="Отклонить">✗</button>`;
        }
        return btns ? `<div class="flex items-center gap-1">${btns}</div>` : '';
      },
      onCellClicked: (params: any) => {
        const target = params.event?.target as HTMLElement;
        const action = target?.closest('[data-action]')?.getAttribute('data-action');
        if (!action || !params.data) return;
        const product = params.data as PromoProductSummary;

        switch (action) {
          case 'participate':
            this.participateMutation.mutate(product.id);
            break;
          case 'decline':
            this.declineMutation.mutate(product.id);
            break;
          case 'approve':
            if (product.actionId) this.approveMutation.mutate(product.actionId);
            break;
          case 'reject':
            if (product.actionId) this.rejectMutation.mutate(product.actionId);
            break;
        }
      },
    },
  ];

  private readonly filter = computed<PromoProductFilter>(() => {
    const vals = this.filterValues();
    const f: PromoProductFilter = {};
    if (vals['participationStatus']?.length) f.participationStatus = vals['participationStatus'];
    if (vals['evaluationResult']?.length) f.evaluationResult = vals['evaluationResult'];
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    if (vals['search']) f.search = vals['search'];
    return f;
  });

  readonly campaignQuery = injectQuery(() => ({
    queryKey: ['promo-campaign', this.wsStore.currentWorkspaceId(), this.campaignId()],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.getCampaign(
          this.wsStore.currentWorkspaceId()!,
          Number(this.campaignId()),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.campaignId(),
  }));

  readonly productsQuery = injectQuery(() => ({
    queryKey: [
      'promo-campaign-products',
      this.wsStore.currentWorkspaceId(),
      this.campaignId(),
      this.filter(),
      this.currentPage(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listCampaignProducts(
          this.wsStore.currentWorkspaceId()!,
          Number(this.campaignId()),
          this.filter(),
          this.currentPage(),
          50,
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.campaignId(),
    staleTime: 30_000,
  }));

  readonly campaign = computed(() => this.campaignQuery.data());
  readonly rows = computed(() => this.productsQuery.data()?.content ?? []);

  readonly campaignStatusLabel = computed(() => {
    const c = this.campaign();
    return c ? CAMPAIGN_STATUS_LABEL[c.status] ?? c.status : '';
  });

  readonly campaignStatusColor = computed((): StatusColor => {
    const c = this.campaign();
    return c ? (CAMPAIGN_STATUS_COLOR[c.status] ?? 'neutral') : 'neutral';
  });

  readonly mpBadge = computed(() => {
    const c = this.campaign();
    return c ? MP_BADGE[c.sourcePlatform] ?? null : null;
  });

  readonly isCampaignFrozenOrEnded = computed(() => {
    const c = this.campaign();
    return c ? c.status === 'FROZEN' || c.status === 'ENDED' || c.status === 'CANCELLED' : false;
  });

  readonly hasActiveFilters = computed(() =>
    Object.values(this.filterValues()).some(
      (v) => v !== '' && v !== null && v !== undefined && (!Array.isArray(v) || v.length > 0),
    ),
  );

  readonly getRowId = (params: any) => String(params.data.id);

  private readonly participateMutation = injectMutation(() => ({
    mutationFn: (promoProductId: number) =>
      lastValueFrom(this.promoApi.participate(this.wsStore.currentWorkspaceId()!, promoProductId, {})),
    onSuccess: () => {
      this.toast.success('Участие подтверждено');
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error('Не удалось подтвердить участие'),
  }));

  private readonly declineMutation = injectMutation(() => ({
    mutationFn: (promoProductId: number) =>
      lastValueFrom(this.promoApi.decline(this.wsStore.currentWorkspaceId()!, promoProductId, {})),
    onSuccess: () => {
      this.toast.success('Товар отклонён');
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error('Не удалось отклонить товар'),
  }));

  private readonly approveMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(this.promoApi.approveAction(this.wsStore.currentWorkspaceId()!, actionId)),
    onSuccess: () => {
      this.toast.success('Действие одобрено');
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error('Не удалось одобрить действие'),
  }));

  private readonly rejectMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(this.promoApi.rejectAction(this.wsStore.currentWorkspaceId()!, actionId, { reason: 'Rejected' })),
    onSuccess: () => {
      this.toast.success('Действие отклонено');
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error('Не удалось отклонить действие'),
  }));

  onFiltersChanged(values: Record<string, any>): void {
    this.filterValues.set(values);
    this.currentPage.set(0);
  }

  navigateBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'promo', 'campaigns']);
  }

  formatDate(iso: string | null): string {
    return formatDateTime(iso, 'date');
  }

  private formatNumber(val: number | null): string {
    if (val == null) return '—';
    return val.toLocaleString('ru-RU');
  }

  private badgeCell(
    value: string | null,
    labels: Record<string, string>,
    colors: Record<string, string>,
  ): string {
    if (!value) return '';
    const label = labels[value] ?? value;
    const color = colors[value] ?? 'neutral';
    const cssVar = `var(--status-${color})`;
    return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
              style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
      <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
      ${label}
    </span>`;
  }
}
