import { ColDef, ICellRendererParams, ValueFormatterParams } from 'ag-grid-community';
import { TranslateService } from '@ngx-translate/core';

import { formatMoney, financeColor, formatDateTime, renderBadge, renderOutlineBadge, renderMarketplaceBadge } from './format.utils';

type Translator = (key: string) => string;

function resolveTranslator(t: TranslateService | Translator): Translator {
  return typeof t === 'function' ? t : (key: string) => t.instant(key);
}

// ---------------------------------------------------------------------------
// Money column
// ---------------------------------------------------------------------------

export interface MoneyColumnOptions {
  decimals?: number;
  fixedNegative?: boolean;
  width?: number;
  sortable?: boolean;
}

export function moneyColumn(
  field: string,
  headerKey: string,
  t: TranslateService | Translator,
  options?: MoneyColumnOptions,
): ColDef {
  const tr = resolveTranslator(t);
  const decimals = options?.decimals ?? 0;
  return {
    field,
    headerName: tr(headerKey),
    type: 'rightAligned',
    cellClass: 'font-mono',
    sortable: options?.sortable ?? true,
    width: options?.width,
    valueFormatter: (p: ValueFormatterParams) => formatMoney(p.value, decimals),
    cellStyle: options?.fixedNegative
      ? () => ({ color: 'var(--finance-negative)' })
      : (p) => ({ color: financeColor(p.value) }),
  };
}

// ---------------------------------------------------------------------------
// Badge column (status-like with colored dot)
// ---------------------------------------------------------------------------

export interface BadgeColumnOptions {
  width?: number;
  sortable?: boolean;
  translatePrefix?: string;
}

export function badgeColumn(
  field: string,
  headerKey: string,
  t: TranslateService | Translator,
  colorMap: Record<string, string>,
  options?: BadgeColumnOptions,
): ColDef {
  const tr = resolveTranslator(t);
  const prefix = options?.translatePrefix;
  return {
    field,
    headerName: tr(headerKey),
    width: options?.width ?? 130,
    sortable: options?.sortable ?? true,
    cellRenderer: (params: ICellRendererParams) => {
      const val = params.value as string;
      if (!val) return '—';
      const label = prefix ? tr(`${prefix}.${val}`) : val;
      const cssVar = colorMap[val] ?? 'var(--status-neutral)';
      return renderBadge(label, cssVar);
    },
  };
}

// ---------------------------------------------------------------------------
// Platform column (marketplace badge from registry)
// ---------------------------------------------------------------------------

export function platformColumn(
  t: TranslateService | Translator,
  field = 'sourcePlatform',
  headerKey = 'analytics.pnl.col.platform',
  width = 90,
): ColDef {
  const tr = resolveTranslator(t);
  return {
    field,
    headerName: tr(headerKey),
    headerTooltip: tr(headerKey),
    width,
    cellRenderer: (p: ICellRendererParams) => {
      const val = p.value as string;
      if (!val) return '—';
      return renderMarketplaceBadge(val);
    },
  };
}

// ---------------------------------------------------------------------------
// Delta percent column (arrows with finance colors)
// ---------------------------------------------------------------------------

export function deltaPercentColumn(
  t: TranslateService | Translator,
  field = 'changePct',
  headerKey = 'Δ%',
  width = 80,
): ColDef {
  return {
    field,
    headerName: typeof headerKey === 'string' && !headerKey.includes('.') ? headerKey : resolveTranslator(t)(headerKey),
    width,
    sortable: true,
    cellClass: 'font-mono text-right',
    cellRenderer: (params: ICellRendererParams) => {
      const v = params.value;
      if (v === null || v === undefined) return '—';
      const abs = Math.abs(v).toFixed(1).replace('.', ',');
      if (v > 0) return `<span style="color: var(--finance-positive)">↑ ${abs}%</span>`;
      if (v < 0) return `<span style="color: var(--finance-negative)">↓ ${abs}%</span>`;
      return `<span style="color: var(--finance-zero)">→ 0%</span>`;
    },
  };
}

// ---------------------------------------------------------------------------
// DateTime column
// ---------------------------------------------------------------------------

export function dateTimeColumn(
  field: string,
  headerKey: string,
  t: TranslateService | Translator,
  style: 'short' | 'full' | 'date' | 'time' = 'full',
  options?: { width?: number; sort?: 'asc' | 'desc' },
): ColDef {
  const tr = resolveTranslator(t);
  return {
    field,
    headerName: tr(headerKey),
    width: options?.width ?? 140,
    sortable: true,
    sort: options?.sort as any,
    valueFormatter: (p: ValueFormatterParams) => formatDateTime(p.value, style),
  };
}

// ---------------------------------------------------------------------------
// Mono column (font-mono text)
// ---------------------------------------------------------------------------

export function monoColumn(
  field: string,
  headerKey: string,
  t: TranslateService | Translator,
  options?: { width?: number; sortable?: boolean },
): ColDef {
  const tr = resolveTranslator(t);
  return {
    field,
    headerName: tr(headerKey),
    cellClass: 'font-mono',
    sortable: options?.sortable ?? true,
    width: options?.width,
  };
}

// ---------------------------------------------------------------------------
// Link column (clickable accent text)
// ---------------------------------------------------------------------------

export function linkColumn(
  field: string,
  headerKey: string,
  t: TranslateService | Translator,
  options?: { minWidth?: number; flex?: number },
): ColDef {
  const tr = resolveTranslator(t);
  return {
    field,
    headerName: tr(headerKey),
    minWidth: options?.minWidth ?? 200,
    flex: options?.flex ?? 1,
    sortable: true,
    cellRenderer: (params: ICellRendererParams) => {
      if (!params.value) return '—';
      return `<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline" title="${params.value}">${params.value}</span>`;
    },
  };
}

// ---------------------------------------------------------------------------
// Outline badge column (neutral tag-like)
// ---------------------------------------------------------------------------

export function outlineBadgeColumn(
  field: string,
  headerKey: string,
  t: TranslateService | Translator,
  translatePrefix?: string,
  options?: { width?: number },
): ColDef {
  const tr = resolveTranslator(t);
  return {
    field,
    headerName: tr(headerKey),
    width: options?.width ?? 150,
    sortable: true,
    cellRenderer: (params: ICellRendererParams) => {
      const val = params.value as string;
      if (!val) return '—';
      const label = translatePrefix ? tr(`${translatePrefix}.${val}`) : val;
      return renderOutlineBadge(label);
    },
  };
}
