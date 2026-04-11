import { TranslateService } from '@ngx-translate/core';
import {
  CellClickedEvent,
  ColDef,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';

import { Mismatch, MismatchType } from '@core/models';
import { renderBadge } from '@shared/utils/format.utils';

const STATUS_BADGE: Record<string, 'success' | 'error' | 'warning' | 'info' | 'neutral'> = {
  ACTIVE: 'error',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
  AUTO_RESOLVED: 'success',
  IGNORED: 'neutral',
};

const TYPE_BADGE_COLOR: Record<MismatchType, string> = {
  PRICE: 'var(--status-info)',
  STOCK: 'var(--status-warning)',
  PROMO: '#7C3AED',
  FINANCE: '#4338CA',
};

const SEVERITY_BADGE_COLOR: Record<string, string> = {
  CRITICAL: 'var(--status-error)',
  WARNING: 'var(--status-warning)',
};

export interface MismatchColumnCallbacks {
  onOfferClick: (row: Mismatch) => void;
  onQuickAck: (row: Mismatch) => void;
}

export function buildMismatchColumnDefs(
    tr: TranslateService,
    callbacks: MismatchColumnCallbacks): ColDef<Mismatch>[] {
  return [
    {
      headerName: tr.instant('mismatches.grid.offer'),
      colId: 'offerName',
      field: 'offerName',
      minWidth: 260,
      flex: 1,
      pinned: 'left',
      sortable: true,
      tooltipValueGetter: (p: any) => p.data?.offerName ?? '',
      cellRenderer: (p: ICellRendererParams<Mismatch>) => {
        const d = p.data;
        if (!d) return '';
        const mp = d.marketplaceType === 'OZON'
          ? '<span class="mr-1.5 inline-block rounded px-1 py-0.5 text-[10px] font-semibold leading-none" style="background:#005BFF;color:#fff">Ozon</span>'
          : '<span class="mr-1.5 inline-block rounded px-1 py-0.5 text-[10px] font-semibold leading-none" style="background:#7B2FBE;color:#fff">WB</span>';
        return `<div class="leading-tight py-1"><div class="flex items-center">${mp}<span class="font-medium text-[var(--accent-primary)] cursor-pointer hover:underline">${esc(d.offerName)}</span></div><div class="font-mono text-[11px] text-[var(--text-secondary)] mt-0.5">${esc(d.skuCode)}</div></div>`;
      },
      onCellClicked: (params: CellClickedEvent<Mismatch>) => {
        if (params.data) callbacks.onOfferClick(params.data);
      },
    },
    {
      headerName: tr.instant('mismatches.grid.type'),
      colId: 'type',
      field: 'type',
      width: 100,
      sortable: true,
      cellRenderer: (p: ICellRendererParams<Mismatch>) => {
        const v = p.data?.type;
        if (!v) return '';
        const color = TYPE_BADGE_COLOR[v] ?? TYPE_BADGE_COLOR.PRICE;
        const label = tr.instant(`mismatches.type.${v}`);
        return renderBadge(esc(label), color);
      },
    },
    {
      headerName: tr.instant('mismatches.grid.expected'),
      colId: 'expectedValue',
      field: 'expectedValue',
      width: 120,
      sortable: true,
      cellClass: 'font-mono text-xs',
      type: 'rightAligned',
    },
    {
      headerName: tr.instant('mismatches.grid.actual'),
      colId: 'actualValue',
      field: 'actualValue',
      width: 120,
      sortable: true,
      type: 'rightAligned',
      cellRenderer: (p: ICellRendererParams<Mismatch>) => {
        const d = p.data;
        if (!d) return '';
        const diff = d.expectedValue !== d.actualValue;
        const cls = diff
          ? 'font-mono text-xs bg-[color-mix(in_srgb,var(--status-error)_12%,transparent)]'
          : 'font-mono text-xs';
        return `<span class="${cls}">${esc(d.actualValue)}</span>`;
      },
    },
    {
      headerName: tr.instant('mismatches.grid.delta'),
      colId: 'deltaPct',
      field: 'deltaPct',
      width: 70,
      sortable: true,
      cellClass: 'font-mono text-xs',
      type: 'rightAligned',
      valueFormatter: (p: ValueFormatterParams<Mismatch>) => fmtDelta(p.value),
    },
    {
      headerName: tr.instant('mismatches.grid.age'),
      colId: 'age',
      field: 'detectedAt',
      width: 110,
      sortable: true,
      cellRenderer: (p: ICellRendererParams<Mismatch>) => {
        const v = p.data?.detectedAt;
        if (!v) return '\u2014';
        const rel = formatDistanceToNow(new Date(v), { locale: ru, addSuffix: true });
        const color = ageColor(v);
        const abs = new Date(v).toLocaleString('ru-RU');
        return `<span style="color:${color}" title="${esc(abs)}">${esc(rel)}</span>`;
      },
    },
    {
      headerName: tr.instant('mismatches.grid.severity'),
      colId: 'severity',
      field: 'severity',
      width: 100,
      sortable: true,
      cellRenderer: (p: ICellRendererParams<Mismatch>) => {
        const sev = p.data?.severity;
        if (!sev) return '';
        const color = SEVERITY_BADGE_COLOR[sev] ?? 'var(--status-warning)';
        const label = tr.instant('mismatches.severity.' + sev);
        return renderBadge(esc(label), color);
      },
    },
    {
      headerName: tr.instant('mismatches.grid.status'),
      colId: 'status',
      field: 'status',
      width: 130,
      sortable: true,
      cellRenderer: (p: ICellRendererParams<Mismatch>) => {
        const st = p.data?.status;
        if (!st) return '';
        const color = STATUS_BADGE[st] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        const label = tr.instant(`mismatches.status.${st}`);
        return renderBadge(esc(label), cssVar);
      },
    },
    {
      headerName: tr.instant('mismatches.grid.resolution'),
      colId: 'resolution',
      field: 'resolution',
      width: 140,
      sortable: true,
      hide: true,
      valueFormatter: (p: ValueFormatterParams<Mismatch>) => {
        const r = p.value as string | null;
        if (!r) return '\u2014';
        return tr.instant(`mismatches.resolution.${r}`);
      },
    },
    {
      headerName: tr.instant('mismatches.grid.connection'),
      colId: 'connectionName',
      field: 'connectionName',
      tooltipField: 'connectionName',
      width: 160,
      sortable: false,
      hide: true,
    },
    {
      headerName: '',
      colId: 'quickAction',
      width: 50,
      sortable: false,
      resizable: false,
      pinned: 'right',
      cellRenderer: (p: ICellRendererParams<Mismatch>) => {
        const d = p.data;
        if (!d || d.status !== 'ACTIVE') return '';
        return `<button data-action="quick-ack" title="${esc(tr.instant('mismatches.grid.quick_ack'))}" class="flex h-full items-center justify-center text-[var(--text-secondary)] hover:text-[var(--status-success)] cursor-pointer"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg></button>`;
      },
      onCellClicked: (params: CellClickedEvent<Mismatch>) => {
        const target = params.event?.target as HTMLElement | null;
        if (target?.closest('[data-action="quick-ack"]') && params.data) {
          callbacks.onQuickAck(params.data);
        }
      },
    },
  ];
}

function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function fmtDelta(v: unknown): string {
  if (v === null || v === undefined) return '\u2014';
  const n = Number(v);
  if (Number.isNaN(n)) return '\u2014';
  return `${n > 0 ? '+' : ''}${n.toFixed(1).replace('.', ',')}%`;
}

function ageColor(detectedAt: string): string {
  const hours = (Date.now() - new Date(detectedAt).getTime()) / 3_600_000;
  if (hours < 1) return 'var(--status-success)';
  if (hours < 6) return 'var(--status-warning)';
  if (hours < 24) return '#EA580C';
  return 'var(--status-error)';
}
