import {
  CellClickedEvent,
  ColDef,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';
import { TranslateService } from '@ngx-translate/core';

import { DraftPriceChange } from '@core/models';
import { platformColumn } from '@shared/utils/column-factories';

const MARGIN_THRESHOLDS = { high: 30, low: 10, negative: 0 };

function formatMoney(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—';
  const abs = Math.abs(v);
  const intPart = Math.floor(abs).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
  const prefix = v < 0 ? '\u2212' : '';
  return `${prefix}${intPart}\u00A0₽`;
}

function moneyFormatter(params: ValueFormatterParams): string {
  return formatMoney(params.value);
}

function parsePositiveNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? value : null;
  }
  if (typeof value === 'string') {
    const normalized = value.replace(/\s+/g, '').replace(',', '.');
    if (!normalized) return null;
    const parsed = Number(normalized);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }
  return null;
}

function percentFormatter(params: ValueFormatterParams): string {
  const v = params.value;
  if (v === null || v === undefined) return '—';
  return `${v.toFixed(1).replace('.', ',')}%`;
}

function velocityFormatter(params: ValueFormatterParams): string {
  const v = params.value;
  if (v === null || v === undefined) return '—';
  if (v === 0) return '0';
  return v.toFixed(1).replace('.', ',');
}

function daysFormatter(params: ValueFormatterParams): string {
  const v = params.value;
  if (v === null || v === undefined) return '—';
  return v.toFixed(1).replace('.', ',');
}

function stockFormatter(params: ValueFormatterParams): string {
  const v = params.value;
  if (v === null || v === undefined) return '—';
  return v.toLocaleString('ru-RU');
}

export interface GridColumnCallbacks {
  onLockToggle?: (offerId: number, currentlyLocked: boolean, currentPrice: number | null) => void;
  onDraftPriceChange?: (offerId: number, newPrice: number, originalPrice: number, costPrice: number | null) => void;
  getDraftChange?: (offerId: number) => DraftPriceChange | undefined;
  onCostPriceChange?: (sellerSkuId: number, offerId: number, newCostPrice: number) => void;
  canEditCost?: boolean;
  onNavigate?: (offerId: number) => void;
}

function buildCurrentPriceCol(
    translate: TranslateService,
    callbacks: GridColumnCallbacks | undefined,
    draftMode: boolean,
): ColDef {
  const base: ColDef = {
    field: 'currentPrice',
    headerName: translate.instant('grid.col.current_price'),
    headerTooltip: translate.instant('grid.col.current_price'),
    width: draftMode ? 180 : 120,
    sortable: true,
    type: 'rightAligned',
    cellClass: 'font-mono text-[length:var(--text-sm)]',
    valueFormatter: moneyFormatter,
  };

  if (!draftMode) return base;

  return {
    ...base,
    editable: (params) => {
      if (params.data?.manualLock) return false;
      if (params.data?.promoStatus === 'PARTICIPATING') return false;
      return true;
    },
    cellEditor: 'agNumberCellEditor',
    cellEditorParams: { min: 0.01, precision: 2 },
    valueSetter: (params) => {
      const newValue = params.newValue;
      if (newValue == null || newValue <= 0) return false;
      callbacks?.onDraftPriceChange?.(
          params.data.offerId,
          newValue,
          params.data.currentPrice ?? 0,
          params.data.costPrice ?? null,
      );
      return false;
    },
    cellRenderer: (params: ICellRendererParams) => {
      const draft = callbacks?.getDraftChange?.(params.data?.offerId);
      if (!draft) return formatMoney(params.value);

      const container = document.createElement('span');

      const oldEl = document.createElement('span');
      oldEl.style.textDecoration = 'line-through';
      oldEl.style.color = 'var(--text-tertiary)';
      oldEl.style.marginRight = '6px';
      oldEl.textContent = formatMoney(draft.originalPrice);

      const newEl = document.createElement('span');
      newEl.style.fontWeight = '700';
      newEl.textContent = formatMoney(draft.newPrice);

      container.append(oldEl, newEl);
      return container;
    },
    cellStyle: (params) => {
      const draft = callbacks?.getDraftChange?.(params.data?.offerId);
      if (draft) {
        return {
          backgroundColor: 'color-mix(in srgb, var(--status-warning) 15%, transparent)',
        };
      }
      return null;
    },
    tooltipValueGetter: (params) => {
      if (params.data?.manualLock) return translate.instant('grid.tooltip.manual_lock');
      if (params.data?.promoStatus === 'PARTICIPATING') return translate.instant('grid.tooltip.promo_lock');
      return undefined;
    },
  };
}

function buildProjectedMarginCol(translate: TranslateService, callbacks: GridColumnCallbacks | undefined): ColDef {
  return {
    colId: 'projectedMargin',
    headerName: translate.instant('grid.col.projected_margin'),
    headerTooltip: translate.instant('grid.col.projected_margin_full'),
    width: 110,
    sortable: false,
    type: 'rightAligned',
    cellClass: 'font-mono text-[length:var(--text-sm)]',
    valueGetter: (params) => {
      const draft = callbacks?.getDraftChange?.(params.data?.offerId);
      if (!draft) return null;
      const costPrice = params.data?.costPrice;
      if (!costPrice || costPrice === 0) return null;
      return ((draft.newPrice - costPrice) / draft.newPrice) * 100;
    },
    valueFormatter: percentFormatter,
    cellStyle: (params) => {
      const v = params.value;
      if (v === null || v === undefined) return null;
      if (v < MARGIN_THRESHOLDS.negative) return { color: 'var(--finance-negative)' };
      if (v < MARGIN_THRESHOLDS.low) return { color: 'var(--status-warning)' };
      if (v >= MARGIN_THRESHOLDS.high) return { color: 'var(--finance-positive)' };
      return null;
    },
  };
}

function buildCostPriceCol(translate: TranslateService, callbacks: GridColumnCallbacks | undefined): ColDef {
  const base: ColDef = {
    field: 'costPrice',
    headerName: translate.instant('grid.col.cost_price'),
    headerTooltip: translate.instant('grid.col.cost_price'),
    width: 120,
    sortable: true,
    type: 'rightAligned',
    cellClass: 'font-mono text-[length:var(--text-sm)]',
    valueFormatter: moneyFormatter,
  };

  if (!callbacks?.canEditCost) return base;

  return {
    ...base,
    editable: true,
    cellEditor: 'agNumberCellEditor',
    cellEditorParams: { min: 0.01, precision: 2 },
    valueSetter: (params) => {
      const newValue = parsePositiveNumber(params.newValue);
      if (newValue == null) return false;
      if (newValue === params.data.costPrice) return false;
      params.data.costPrice = newValue;
      if (params.data.currentPrice && newValue > 0) {
        params.data.marginPct =
          ((params.data.currentPrice - newValue) / params.data.currentPrice) * 100;
      }
      callbacks?.onCostPriceChange?.(params.data.sellerSkuId, params.data.offerId, newValue);
      return true;
    },
    cellClass: 'font-mono text-[length:var(--text-sm)] dp-editable-cell',
    cellStyle: () => ({
      cursor: 'text',
    }),
    tooltipValueGetter: () => translate.instant('grid.tooltip.double_click_edit'),
  };
}

export function buildGridColumnDefs(
    translate: TranslateService,
    callbacks?: GridColumnCallbacks,
    draftMode = false,
): ColDef[] {
  const cols: ColDef[] = [
    {
      field: 'skuCode',
      headerName: translate.instant('grid.col.sku'),
      headerTooltip: translate.instant('grid.col.sku'),
      width: 130,
      pinned: 'left' as const,
      lockPosition: true,
      sortable: true,
      tooltipField: 'skuCode',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      cellRenderer: (params: ICellRendererParams) => {
        if (!params.value) return '';
        return `<span class="text-[var(--accent-primary)] cursor-pointer hover:underline">${params.value}</span>`;
      },
      onCellClicked: (params: CellClickedEvent) => {
        const offerId = params.data?.offerId;
        if (offerId != null && callbacks?.onNavigate) {
          callbacks.onNavigate(offerId);
        }
      },
    },
    {
      field: 'productName',
      headerName: translate.instant('grid.col.name'),
      width: 250,
      minWidth: 150,
      sortable: true,
      tooltipField: 'productName',
      cellClass: 'text-[length:var(--text-sm)]',
    },
    { ...platformColumn(translate, 'marketplaceType', 'grid.col.marketplace', 75), headerTooltip: translate.instant('grid.col.marketplace_full'), sortable: false },
    buildCurrentPriceCol(translate, callbacks, draftMode),
    {
      field: 'marginPct',
      headerName: translate.instant('grid.col.margin'),
      headerTooltip: translate.instant('grid.col.margin'),
      width: 90,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: percentFormatter,
      cellStyle: (params) => {
        const v = params.value;
        if (v === null || v === undefined) return null;
        if (v >= MARGIN_THRESHOLDS.high) return { color: 'var(--finance-positive)' };
        if (v < MARGIN_THRESHOLDS.negative) return { color: 'var(--finance-negative)' };
        if (v < MARGIN_THRESHOLDS.low) return { color: 'var(--status-warning)' };
        return null;
      },
    },
    buildCostPriceCol(translate, callbacks),
  ];

  if (draftMode) {
    cols.push(buildProjectedMarginCol(translate, callbacks));
  }

  cols.push(
    {
      field: 'availableStock',
      headerName: translate.instant('grid.col.stock'),
      headerTooltip: translate.instant('grid.col.stock'),
      width: 90,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: stockFormatter,
      cellStyle: (params) => {
        if (params.value === 0) return { color: 'var(--finance-negative)', fontWeight: '700' };
        return null;
      },
    },
    {
      field: 'velocity14d',
      headerName: translate.instant('grid.col.velocity'),
      headerTooltip: translate.instant('grid.col.velocity_full'),
      width: 90,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: velocityFormatter,
      cellStyle: (params) => {
        if (params.value === 0) return { color: 'var(--text-tertiary)', fontStyle: 'italic' };
        return null;
      },
    },
    {
      field: 'lastDecision',
      headerName: translate.instant('grid.col.decision'),
      headerTooltip: translate.instant('grid.col.decision'),
      width: 110,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? translate.instant('grid.decision.' + p.value) : '—',
    },
    {
      field: 'lastActionStatus',
      headerName: translate.instant('grid.col.action_status'),
      headerTooltip: translate.instant('grid.col.action_status'),
      width: 130,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? translate.instant('grid.action_status.' + p.value) : '—',
    },
    {
      field: 'promoStatus',
      headerName: translate.instant('grid.col.promo'),
      headerTooltip: translate.instant('grid.col.promo'),
      width: 110,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? translate.instant('grid.promo_status.' + p.value) : '—',
    },
    {
      field: 'manualLock',
      headerName: translate.instant('grid.col.lock'),
      headerTooltip: translate.instant('grid.col.lock_full'),
      width: 80,
      sortable: false,
      cellRenderer: (params: ICellRendererParams) => {
        const locked = !!params.value;
        const cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = locked;
        cb.className = 'cursor-pointer accent-[var(--accent-primary)]';
        cb.style.width = '16px';
        cb.style.height = '16px';
        cb.addEventListener('click', (e) => {
          e.stopPropagation();
          callbacks?.onLockToggle?.(params.data.offerId, locked, params.data.currentPrice);
        });
        const wrapper = document.createElement('div');
        wrapper.className = 'flex items-center justify-center h-full';
        wrapper.appendChild(cb);
        return wrapper;
      },
    },
    {
      field: 'dataFreshness',
      headerName: translate.instant('grid.col.freshness'),
      headerTooltip: translate.instant('grid.col.freshness_full'),
      width: 80,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value === 'FRESH' ? '●' : p.value === 'STALE' ? '●' : '—',
      cellStyle: (params) => {
        if (params.value === 'FRESH') return { color: 'var(--status-success)' };
        if (params.value === 'STALE') return { color: 'var(--status-error)' };
        return { color: 'var(--text-tertiary)' };
      },
    },
    {
      field: 'connectionName',
      headerName: translate.instant('grid.col.connection'),
      width: 150,
      sortable: false,
      hide: true,
      tooltipField: 'connectionName',
      cellClass: 'text-[length:var(--text-sm)]',
    },
    {
      field: 'status',
      headerName: translate.instant('grid.col.status'),
      headerTooltip: translate.instant('grid.col.status'),
      width: 100,
      sortable: false,
      hide: true,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        translate.instant('grid.offer_status.' + p.value),
    },
    {
      field: 'category',
      headerName: translate.instant('grid.col.category'),
      width: 140,
      sortable: false,
      hide: true,
      tooltipField: 'category',
      cellClass: 'text-[length:var(--text-sm)]',
    },
    {
      field: 'discountPrice',
      headerName: translate.instant('grid.col.discount_price'),
      headerTooltip: translate.instant('grid.col.discount_price'),
      width: 110,
      sortable: false,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: moneyFormatter,
    },
    {
      field: 'daysOfCover',
      headerName: translate.instant('grid.col.days_of_cover'),
      headerTooltip: translate.instant('grid.col.days_of_cover'),
      width: 90,
      sortable: true,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: daysFormatter,
      cellStyle: (params) => {
        const v = params.value;
        if (v === null || v === undefined) return null;
        if (v < 7) return { color: 'var(--finance-negative)' };
        if (v < 14) return { color: 'var(--status-warning)' };
        return null;
      },
    },
    {
      field: 'stockRisk',
      headerName: translate.instant('grid.col.stock_risk'),
      headerTooltip: translate.instant('grid.col.stock_risk'),
      width: 100,
      sortable: false,
      hide: true,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? translate.instant('grid.stock_risk.' + p.value) : '—',
    },
    {
      field: 'revenue30d',
      headerName: translate.instant('grid.col.revenue_30d'),
      headerTooltip: translate.instant('grid.col.revenue_30d_full'),
      width: 120,
      sortable: true,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: moneyFormatter,
    },
    {
      field: 'netPnl30d',
      headerName: translate.instant('grid.col.pnl_30d'),
      headerTooltip: translate.instant('grid.col.pnl_30d_full'),
      width: 120,
      sortable: true,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: moneyFormatter,
      cellStyle: (params) => {
        const v = params.value;
        if (v === null || v === undefined) return null;
        if (v > 0) return { color: 'var(--finance-positive)' };
        if (v < 0) return { color: 'var(--finance-negative)' };
        return { color: 'var(--finance-zero)' };
      },
    },
    {
      field: 'returnRatePct',
      headerName: translate.instant('grid.col.return_rate'),
      headerTooltip: translate.instant('grid.col.return_rate'),
      width: 90,
      sortable: true,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: percentFormatter,
      cellStyle: (params) => {
        const v = params.value;
        if (v === null || v === undefined) return null;
        if (v > 10) return { color: 'var(--finance-negative)' };
        if (v > 5) return { color: 'var(--status-warning)' };
        return null;
      },
    },
    {
      field: 'activePolicy',
      headerName: translate.instant('grid.col.policy'),
      width: 160,
      sortable: false,
      hide: true,
      tooltipField: 'activePolicy',
      cellClass: 'text-[length:var(--text-sm)]',
      valueFormatter: (p: ValueFormatterParams) => p.value ?? '—',
    },
    {
      field: 'bidPolicyName',
      headerName: translate.instant('grid.col.bid_policy'),
      width: 160,
      sortable: false,
      tooltipField: 'bidPolicyName',
      cellClass: 'text-[length:var(--text-sm)]',
      valueFormatter: (p: ValueFormatterParams) => p.value ?? '—',
    },
    {
      field: 'currentBid',
      headerName: translate.instant('grid.col.current_bid'),
      width: 110,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: (p: ValueFormatterParams) => {
        if (p.value === null || p.value === undefined) return '—';
        return formatMoney(p.value / 100);
      },
    },
    {
      field: 'lastBidDecisionType',
      headerName: translate.instant('grid.col.last_bid_decision'),
      width: 130,
      sortable: false,
      cellClass: 'text-center',
      cellRenderer: (params: ICellRendererParams) => {
        if (!params.value) return '—';
        const colors: Record<string, string> = {
          BID_UP: 'var(--status-success)',
          BID_DOWN: 'var(--status-error)',
          HOLD: 'var(--status-neutral)',
          PAUSE: 'var(--status-warning)',
          RESUME: 'var(--status-info)',
          SET_MINIMUM: 'var(--status-info)',
          EMERGENCY_CUT: 'var(--status-error)',
        };
        const color = colors[params.value] ?? 'var(--status-neutral)';
        const label = translate.instant('bidding.decision.' + params.value);
        return `<span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium" style="background:color-mix(in srgb,${color} 15%,transparent);color:${color}">${label}</span>`;
      },
    },
    {
      field: 'bidDrrPct',
      headerName: translate.instant('grid.col.bid_drr'),
      width: 100,
      sortable: true,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: percentFormatter,
    },
    {
      field: 'manualBidLock',
      headerName: translate.instant('grid.col.manual_bid_lock'),
      width: 100,
      sortable: false,
      cellClass: 'text-center',
      cellRenderer: (params: ICellRendererParams) => {
        if (!params.value) return '—';
        return `<span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium" style="background:color-mix(in srgb,var(--status-warning) 15%,transparent);color:var(--status-warning)">🔒</span>`;
      },
    },
    {
      field: 'lastSyncAt',
      headerName: translate.instant('grid.col.last_sync'),
      headerTooltip: translate.instant('grid.col.last_sync_full'),
      width: 120,
      sortable: true,
      hide: true,
      cellClass: 'text-[length:var(--text-sm)]',
      valueFormatter: (p: ValueFormatterParams) => {
        if (!p.value) return '—';
        const date = new Date(p.value);
        if (isNaN(date.getTime())) return '—';
        const diffMs = Date.now() - date.getTime();
        const minutes = Math.floor(diffMs / 60_000);
        if (minutes < 1) return translate.instant('grid.sync.just_now');
        if (minutes < 60) return translate.instant('grid.sync.minutes_ago', { minutes });
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return translate.instant('grid.sync.hours_ago', { hours });
        const days = Math.floor(hours / 24);
        return translate.instant('grid.sync.days_ago', { days });
      },
    },
  );

  return cols;
}
