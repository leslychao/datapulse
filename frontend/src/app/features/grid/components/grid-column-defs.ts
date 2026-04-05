import { ColDef, ICellRendererParams, ValueFormatterParams } from 'ag-grid-community';

const MARGIN_THRESHOLDS = { high: 30, low: 10, negative: 0 };

function moneyFormatter(params: ValueFormatterParams): string {
  const v = params.value;
  if (v === null || v === undefined) return '—';
  const abs = Math.abs(v);
  const intPart = Math.floor(abs).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
  const prefix = v < 0 ? '\u2212' : '';
  return `${prefix}${intPart}\u00A0₽`;
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

const MARKETPLACE_LABELS: Record<string, string> = { WB: 'WB', OZON: 'Ozon' };
const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Активный',
  ARCHIVED: 'Архив',
  BLOCKED: 'Заблокирован',
  INACTIVE: 'Неактивен',
};
const DECISION_LABELS: Record<string, string> = { CHANGE: 'Изменение', SKIP: 'Пропуск', HOLD: 'Удержание' };
const ACTION_STATUS_LABELS: Record<string, string> = {
  PENDING_APPROVAL: 'Ожидает',
  APPROVED: 'Одобрено',
  SCHEDULED: 'Запланировано',
  EXECUTING: 'Выполняется',
  SUCCEEDED: 'Выполнено',
  FAILED: 'Ошибка',
  ON_HOLD: 'Приостановлено',
  EXPIRED: 'Просрочено',
  CANCELLED: 'Отменено',
  SUPERSEDED: 'Заменено',
  RETRY_SCHEDULED: 'Повтор',
  RECONCILIATION_PENDING: 'Сверка',
};
const STOCK_RISK_LABELS: Record<string, string> = { CRITICAL: 'Критический', WARNING: 'Предупреждение', NORMAL: 'Нормальный' };
const PROMO_LABELS: Record<string, string> = { PARTICIPATING: 'Участвует', ELIGIBLE: 'Доступно' };

export interface GridColumnCallbacks {
  onLockToggle?: (offerId: number, currentlyLocked: boolean, currentPrice: number | null) => void;
}

export function buildGridColumnDefs(callbacks?: GridColumnCallbacks): ColDef[] {
  return [
    {
      field: 'skuCode',
      headerName: 'Артикул',
      width: 130,
      pinned: 'left' as const,
      lockPosition: true,
      sortable: true,
      cellClass: 'font-mono text-[length:var(--text-sm)]',
    },
    {
      field: 'productName',
      headerName: 'Название',
      width: 250,
      minWidth: 150,
      sortable: true,
      tooltipField: 'productName',
      cellClass: 'text-[length:var(--text-sm)]',
    },
    {
      field: 'marketplaceType',
      headerName: 'МП',
      width: 65,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) => MARKETPLACE_LABELS[p.value] ?? p.value,
    },
    {
      field: 'currentPrice',
      headerName: 'Текущая цена',
      width: 120,
      sortable: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: moneyFormatter,
    },
    {
      field: 'marginPct',
      headerName: 'Маржа',
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
    {
      field: 'availableStock',
      headerName: 'Остаток',
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
      headerName: 'Скорость',
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
      headerName: 'Решение',
      width: 110,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? (DECISION_LABELS[p.value] ?? p.value) : '—',
    },
    {
      field: 'lastActionStatus',
      headerName: 'Статус действия',
      width: 130,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? (ACTION_STATUS_LABELS[p.value] ?? p.value) : '—',
    },
    {
      field: 'promoStatus',
      headerName: 'Промо',
      width: 110,
      sortable: false,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? (PROMO_LABELS[p.value] ?? p.value) : '—',
    },
    {
      field: 'manualLock',
      headerName: 'Блоки...',
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
      headerName: 'Свежесть',
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
      headerName: 'Подключение',
      width: 150,
      sortable: false,
      hide: true,
      cellClass: 'text-[length:var(--text-sm)]',
    },
    {
      field: 'status',
      headerName: 'Статус',
      width: 100,
      sortable: false,
      hide: true,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) => STATUS_LABELS[p.value] ?? p.value,
    },
    {
      field: 'category',
      headerName: 'Категория',
      width: 140,
      sortable: false,
      hide: true,
      cellClass: 'text-[length:var(--text-sm)]',
    },
    {
      field: 'discountPrice',
      headerName: 'Цена со скидкой',
      width: 110,
      sortable: false,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: moneyFormatter,
    },
    {
      field: 'costPrice',
      headerName: 'Себестоимость',
      width: 110,
      sortable: false,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: moneyFormatter,
    },
    {
      field: 'daysOfCover',
      headerName: 'Дней покрытия',
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
      headerName: 'Риск остатка',
      width: 100,
      sortable: false,
      hide: true,
      cellClass: 'text-center',
      valueFormatter: (p: ValueFormatterParams) =>
        p.value ? (STOCK_RISK_LABELS[p.value] ?? p.value) : '—',
    },
    {
      field: 'revenue30d',
      headerName: 'Выручка 30д',
      width: 120,
      sortable: true,
      hide: true,
      type: 'rightAligned',
      cellClass: 'font-mono text-[length:var(--text-sm)]',
      valueFormatter: moneyFormatter,
    },
    {
      field: 'netPnl30d',
      headerName: 'P&L 30д',
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
      headerName: '% возвратов',
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
      headerName: 'Ценовая политика',
      width: 160,
      sortable: false,
      hide: true,
      cellClass: 'text-[length:var(--text-sm)]',
      valueFormatter: (p: ValueFormatterParams) => p.value ?? '—',
    },
    {
      field: 'lastSyncAt',
      headerName: 'Последняя синхр.',
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
        if (minutes < 1) return 'только что';
        if (minutes < 60) return `${minutes} мин назад`;
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return `${hours} ч назад`;
        const days = Math.floor(hours / 24);
        return `${days} дн назад`;
      },
    },
  ];
}
