import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { startWith } from 'rxjs';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { Users, CheckCircle, XCircle, Clock, CheckCheck, AlertTriangle } from 'lucide-angular';

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

const ACTION_STATUSES: PromoActionStatus[] = [
  'PENDING_APPROVAL', 'APPROVED', 'EXECUTING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED',
];
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { RbacService } from '@core/auth/rbac.service';
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

const PARTICIPATION_STATUSES: ParticipationStatus[] = [
  'ELIGIBLE', 'PARTICIPATING', 'DECLINED', 'REMOVED', 'BANNED', 'AUTO_DECLINED',
];

const PARTICIPATION_COLOR: Record<ParticipationStatus, string> = {
  ELIGIBLE: 'info',
  PARTICIPATING: 'success',
  DECLINED: 'neutral',
  REMOVED: 'neutral',
  BANNED: 'error',
  AUTO_DECLINED: 'neutral',
};

const EVALUATION_RESULTS: EvaluationResult[] = [
  'PROFITABLE', 'MARGINAL', 'UNPROFITABLE', 'INSUFFICIENT_STOCK', 'INSUFFICIENT_DATA',
];

const EVAL_COLOR: Record<EvaluationResult, string> = {
  PROFITABLE: 'success',
  MARGINAL: 'warning',
  UNPROFITABLE: 'error',
  INSUFFICIENT_STOCK: 'warning',
  INSUFFICIENT_DATA: 'warning',
};

const DECISION_TYPES: PromoDecisionType[] = [
  'PARTICIPATE', 'DECLINE', 'DEACTIVATE', 'PENDING_REVIEW',
];

const DECISION_COLOR: Record<PromoDecisionType, string> = {
  PARTICIPATE: 'success',
  DECLINE: 'neutral',
  DEACTIVATE: 'error',
  PENDING_REVIEW: 'warning',
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

const MP_BADGE: Record<
  string,
  { bg: string; color: string; borderColor: string; label: string }
> = {
  WB: {
    bg: 'var(--mp-wb-bg)',
    color: 'var(--mp-wb)',
    borderColor: 'var(--mp-wb)',
    label: 'WB',
  },
  OZON: {
    bg: 'var(--mp-ozon-bg)',
    color: 'var(--mp-ozon)',
    borderColor: 'var(--mp-ozon)',
    label: 'Ozon',
  },
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
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  protected readonly UsersIcon = Users;
  protected readonly CheckCircleIcon = CheckCircle;
  protected readonly XCircleIcon = XCircle;
  protected readonly ClockIcon = Clock;
  protected readonly CheckCheckIcon = CheckCheck;
  protected readonly AlertTriangleIcon = AlertTriangle;

  readonly campaignId = input.required<string>();

  private readonly translationChange = toSignal(
    this.translate.onTranslationChange.pipe(startWith(null)),
  );

  readonly filterValues = signal<Record<string, any>>({});
  readonly currentPage = signal(0);
  readonly showRejectPopup = signal(false);
  readonly rejectReason = signal('');
  readonly rejectTargetActionId = signal<number | null>(null);
  readonly showDeactivatePopup = signal(false);
  readonly deactivateReason = signal('');
  readonly deactivateTargetProductId = signal<number | null>(null);
  readonly showCancelPopup = signal(false);
  readonly cancelReason = signal('');
  readonly cancelTargetActionId = signal<number | null>(null);
  readonly selectedActionIds = signal<number[]>([]);

  readonly filterConfigs: FilterConfig[] = [
    {
      key: 'participationStatus',
      label: 'promo.detail.filter.participation_status',
      type: 'multi-select',
      options: PARTICIPATION_STATUSES.map(value => ({
        value,
        label: `promo.participation_status.${value}`,
      })),
    },
    {
      key: 'evaluationResult',
      label: 'promo.detail.filter.evaluation',
      type: 'multi-select',
      options: EVALUATION_RESULTS.map(value => ({
        value,
        label: `promo.evaluation_result.${value}`,
      })),
    },
    {
      key: 'decisionType',
      label: 'promo.detail.filter.decision',
      type: 'multi-select',
      options: DECISION_TYPES.map(value => ({
        value,
        label: `promo.decision_type.${value}`,
      })),
    },
    {
      key: 'actionStatus',
      label: 'promo.detail.filter.action_status',
      type: 'multi-select',
      options: ACTION_STATUSES.map(value => ({
        value,
        label: `promo.action_status.${value}`,
      })),
    },
    { key: 'search', label: 'promo.detail.filter.search', type: 'text' },
  ];

  readonly columnDefs = computed(() => {
    this.translationChange();
    return [
    {
      headerName: this.translate.instant('promo.detail.col.product'),
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
      headerName: this.translate.instant('promo.detail.col.seller_sku'),
      field: 'sellerSkuCode',
      width: 120,
      cellClass: 'font-mono',
      valueFormatter: (params: any) => params.value ?? '—',
    },
    {
      headerName: this.translate.instant('promo.detail.col.promo_price'),
      field: 'requiredPrice',
      width: 110,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) => formatMoney(params.value),
    },
    {
      headerName: this.translate.instant('promo.detail.col.max_promo_price'),
      field: 'maxPromoPrice',
      width: 120,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => formatMoney(params.value),
    },
    {
      headerName: this.translate.instant('promo.detail.col.current_price'),
      field: 'currentPrice',
      width: 110,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) => formatMoney(params.value),
    },
    {
      headerName: this.translate.instant('promo.detail.col.discount'),
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
      headerName: this.translate.instant('promo.detail.col.margin'),
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
      headerName: this.translate.instant('promo.detail.col.stock'),
      field: 'stockAvailable',
      width: 80,
      cellClass: 'font-mono text-right',
      sortable: true,
      valueFormatter: (params: any) => this.formatNumber(params.value),
    },
    {
      headerName: this.translate.instant('promo.detail.col.stock_days'),
      field: 'stockDaysOfCover',
      width: 90,
      cellClass: 'font-mono text-right',
      valueFormatter: (params: any) => params.value != null ? `${params.value}` : '—',
    },
    {
      headerName: this.translate.instant('promo.detail.col.evaluation'),
      field: 'evaluationResult',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => this.badgeCell(params.value, 'promo.evaluation_result', EVAL_COLOR),
    },
    {
      headerName: this.translate.instant('promo.detail.col.decision'),
      field: 'decisionType',
      width: 130,
      sortable: true,
      cellRenderer: (params: any) => this.badgeCell(params.value, 'promo.decision_type', DECISION_COLOR),
    },
    {
      headerName: this.translate.instant('promo.detail.col.participation'),
      field: 'participationStatus',
      width: 140,
      sortable: true,
      cellRenderer: (params: any) => this.badgeCell(params.value, 'promo.participation_status', PARTICIPATION_COLOR),
    },
    {
      headerName: this.translate.instant('promo.detail.col.action_status'),
      field: 'actionStatus',
      width: 140,
      cellRenderer: (params: any) => this.badgeCell(params.value, 'promo.action_status', ACTION_COLOR),
    },
    {
      headerName: '',
      field: 'actions',
      width: 200,
      sortable: false,
      suppressMovable: true,
      cellRenderer: (params: any) => {
        if (!params.data) return '';
        const p = params.data as PromoProductSummary;
        const frozen = this.isCampaignFrozenOrEnded();
        if (frozen) return '';
        const canOperate = this.rbac.canOperatePromo();
        const canApprove = this.rbac.canApprovePromo();
        if (!canOperate && !canApprove) return '';

        const checkIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>`;
        const xIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>`;
        const banIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m4.9 4.9 14.2 14.2"/></svg>`;
        let btns = '';
        if (canOperate) {
          if (
            (p.participationStatus === 'ELIGIBLE' && !p.actionId) ||
            p.participationStatus === 'DECLINED' ||
            p.participationStatus === 'AUTO_DECLINED'
          ) {
            btns += `<button class="action-btn" data-action="participate" title="${this.translate.instant('promo.detail.action.participate')}">${checkIcon}</button>`;
          }
          if (p.participationStatus === 'ELIGIBLE' && !p.actionId) {
            btns += `<button class="action-btn" data-action="decline" title="${this.translate.instant('promo.detail.action.decline')}">${xIcon}</button>`;
          }
          if (p.participationStatus === 'PARTICIPATING') {
            btns += `<button class="action-btn" data-action="deactivate" title="${this.translate.instant('promo.detail.action.deactivate')}">${banIcon}</button>`;
          }
        }
        if (canApprove && p.actionStatus === 'PENDING_APPROVAL') {
          btns += `<button class="action-btn" data-action="approve" title="${this.translate.instant('promo.detail.action.approve')}">${checkIcon}</button>`;
          btns += `<button class="action-btn" data-action="reject" title="${this.translate.instant('promo.detail.action.reject')}">${xIcon}</button>`;
        }
        if (canOperate && p.actionId && (p.actionStatus === 'PENDING_APPROVAL' || p.actionStatus === 'APPROVED')) {
          btns += `<button class="action-btn" data-action="cancel" title="${this.translate.instant('promo.detail.action.cancel')}">${xIcon}</button>`;
        }
        return btns ? `<div class="flex items-center gap-0.5">${btns}</div>` : '';
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
            if (product.actionId) {
              this.rejectTargetActionId.set(product.actionId);
              this.rejectReason.set('');
              this.showRejectPopup.set(true);
            }
            break;
          case 'cancel':
            if (product.actionId) {
              this.cancelTargetActionId.set(product.actionId);
              this.cancelReason.set('');
              this.showCancelPopup.set(true);
            }
            break;
          case 'deactivate':
            this.deactivateTargetProductId.set(product.id);
            this.deactivateReason.set('');
            this.showDeactivatePopup.set(true);
            break;
        }
      },
    },
  ];
  });

  private readonly filter = computed<PromoProductFilter>(() => {
    const vals = this.filterValues();
    const f: PromoProductFilter = {};
    if (vals['participationStatus']?.length) f.participationStatus = vals['participationStatus'];
    if (vals['evaluationResult']?.length) f.evaluationResult = vals['evaluationResult'];
    if (vals['decisionType']?.length) f.decisionType = vals['decisionType'];
    if (vals['actionStatus']?.length) f.actionStatus = vals['actionStatus'];
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
    return c ? this.translate.instant(`promo.campaigns.status.${c.status}`) : '';
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
      this.toast.success(this.translate.instant('promo.detail.toast.participate_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.participate_error')),
  }));

  private readonly declineMutation = injectMutation(() => ({
    mutationFn: (promoProductId: number) =>
      lastValueFrom(this.promoApi.decline(this.wsStore.currentWorkspaceId()!, promoProductId, {})),
    onSuccess: () => {
      this.toast.success(this.translate.instant('promo.detail.toast.decline_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.decline_error')),
  }));

  private readonly approveMutation = injectMutation(() => ({
    mutationFn: (actionId: number) =>
      lastValueFrom(this.promoApi.approveAction(this.wsStore.currentWorkspaceId()!, actionId)),
    onSuccess: () => {
      this.toast.success(this.translate.instant('promo.detail.toast.approve_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.approve_error')),
  }));

  private readonly rejectMutation = injectMutation(() => ({
    mutationFn: (payload: { actionId: number; reason: string }) =>
      lastValueFrom(this.promoApi.rejectAction(this.wsStore.currentWorkspaceId()!, payload.actionId, { reason: payload.reason })),
    onSuccess: () => {
      this.showRejectPopup.set(false);
      this.toast.success(this.translate.instant('promo.detail.toast.reject_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.reject_error')),
  }));

  private readonly cancelMutation = injectMutation(() => ({
    mutationFn: (payload: { actionId: number; reason: string }) =>
      lastValueFrom(this.promoApi.cancelAction(this.wsStore.currentWorkspaceId()!, payload.actionId, { cancelReason: payload.reason })),
    onSuccess: () => {
      this.showCancelPopup.set(false);
      this.toast.success(this.translate.instant('promo.detail.toast.cancel_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.cancel_error')),
  }));

  private readonly deactivateMutation = injectMutation(() => ({
    mutationFn: (payload: { productId: number; reason: string }) =>
      lastValueFrom(this.promoApi.deactivate(this.wsStore.currentWorkspaceId()!, payload.productId, { reason: payload.reason })),
    onSuccess: () => {
      this.showDeactivatePopup.set(false);
      this.toast.success(this.translate.instant('promo.detail.toast.deactivate_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.deactivate_error')),
  }));

  private readonly bulkApproveMutation = injectMutation(() => ({
    mutationFn: (actionIds: number[]) =>
      lastValueFrom(this.promoApi.bulkApprove(this.wsStore.currentWorkspaceId()!, { actionIds })),
    onSuccess: () => {
      this.selectedActionIds.set([]);
      this.toast.success(this.translate.instant('promo.detail.toast.bulk_approve_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.bulk_approve_error')),
  }));

  private readonly bulkRejectMutation = injectMutation(() => ({
    mutationFn: (payload: { actionIds: number[]; reason: string }) =>
      lastValueFrom(this.promoApi.bulkReject(this.wsStore.currentWorkspaceId()!, payload)),
    onSuccess: () => {
      this.selectedActionIds.set([]);
      this.toast.success(this.translate.instant('promo.detail.toast.bulk_reject_success'));
      this.queryClient.invalidateQueries({ queryKey: ['promo-campaign-products'] });
    },
    onError: () => this.toast.error(this.translate.instant('promo.detail.toast.bulk_reject_error')),
  }));

  readonly pendingApprovalIds = computed(() =>
    this.rows()
      .filter((p) => p.actionStatus === 'PENDING_APPROVAL' && p.actionId)
      .map((p) => p.actionId!),
  );

  submitReject(): void {
    const id = this.rejectTargetActionId();
    if (id) this.rejectMutation.mutate({ actionId: id, reason: this.rejectReason() });
  }

  submitCancel(): void {
    const id = this.cancelTargetActionId();
    if (id) this.cancelMutation.mutate({ actionId: id, reason: this.cancelReason() });
  }

  submitDeactivate(): void {
    const id = this.deactivateTargetProductId();
    if (id) this.deactivateMutation.mutate({ productId: id, reason: this.deactivateReason() });
  }

  bulkApproveAll(): void {
    const ids = this.pendingApprovalIds();
    if (ids.length > 0) this.bulkApproveMutation.mutate(ids);
  }

  bulkRejectAll(): void {
    const ids = this.pendingApprovalIds();
    if (ids.length > 0) this.bulkRejectMutation.mutate({ actionIds: ids, reason: 'Bulk rejected' });
  }

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
    i18nPrefix: string,
    colors: Record<string, string>,
  ): string {
    if (!value) return '';
    const label = this.translate.instant(`${i18nPrefix}.${value}`);
    const color = colors[value] ?? 'neutral';
    const cssVar = `var(--status-${color})`;
    return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
              style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
      <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
      ${label}
    </span>`;
  }
}
