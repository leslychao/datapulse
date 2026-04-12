import { MarketplaceType, getMarketplaceConfig } from '@core/models';

export function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function formatMoney(
  value: number | null | undefined,
  decimals = 2,
  currency = '₽',
): string {
  if (value === null || value === undefined) return '—';
  const abs = Math.abs(value);
  const fixed = abs.toFixed(decimals);
  const [intPart, decPart] = fixed.split('.');
  const withSeparator = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
  const number = decPart ? `${withSeparator},${decPart}` : withSeparator;
  const prefix = value < 0 ? '\u2212' : '';
  return `${prefix}${number}\u00A0${currency}`;
}

export function formatMoneyWithSign(
  value: number | null | undefined,
  decimals = 2,
  currency = '₽',
): string {
  if (value === null || value === undefined) return '—';
  const abs = Math.abs(value);
  const fixed = abs.toFixed(decimals);
  const [intPart, decPart] = fixed.split('.');
  const withSeparator = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
  const number = decPart ? `${withSeparator},${decPart}` : withSeparator;
  const prefix = value < 0 ? '\u2212' : value > 0 ? '+' : '';
  return `${prefix}${number}\u00A0${currency}`;
}

export function formatPercent(
  value: number | null | undefined,
  decimals = 1,
  showSign = false,
): string {
  if (value === null || value === undefined) return '—';
  const abs = Math.abs(value);
  const fixed = abs.toFixed(decimals).replace('.', ',');
  if (value < 0) return `\u2212${fixed}%`;
  if (value > 0 && showSign) return `+${fixed}%`;
  return `${fixed}%`;
}

export function formatDateTime(
  iso: string | null | undefined,
  style: 'short' | 'full' | 'date' | 'time' = 'full',
): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '—';

  const options: Intl.DateTimeFormatOptions = (() => {
    switch (style) {
      case 'short':
        return { day: '2-digit', month: '2-digit', year: 'numeric' };
      case 'date':
        return { day: 'numeric', month: 'short', year: 'numeric' };
      case 'time':
        return { hour: '2-digit', minute: '2-digit' };
      case 'full':
      default:
        return {
          day: '2-digit', month: '2-digit', year: 'numeric',
          hour: '2-digit', minute: '2-digit',
        };
    }
  })();

  return new Intl.DateTimeFormat('ru-RU', options).format(d);
}

export function formatRelativeTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '—';

  const diffMs = Date.now() - d.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  const diffHrs = Math.floor(diffMs / 3_600_000);
  const diffDays = Math.floor(diffMs / 86_400_000);

  if (diffMin < 1) return 'только что';
  if (diffMin < 60) return `${diffMin} мин назад`;
  if (diffHrs < 24) return `${diffHrs} ч назад`;
  if (diffDays < 30) return `${diffDays} дн назад`;
  return formatDateTime(iso, 'date');
}

export function formatInteger(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return String(Math.round(value)).replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');
}

export function financeColor(value: number | null | undefined): string {
  if (value === null || value === undefined || value === 0) return 'var(--finance-zero)';
  return value > 0 ? 'var(--finance-positive)' : 'var(--finance-negative)';
}

const BADGE_STYLE =
  'display:inline-flex;align-items:center;gap:6px;border-radius:9999px;' +
  'padding:2px 10px;font-size:11px;font-weight:500;line-height:16px;white-space:nowrap;';

const DOT_STYLE =
  'display:inline-block;width:6px;height:6px;border-radius:9999px;flex-shrink:0;';

const OUTLINE_BADGE_STYLE =
  'display:inline-flex;align-items:center;border-radius:9999px;' +
  'padding:2px 8px;font-size:11px;font-weight:500;line-height:16px;white-space:nowrap;' +
  'border:1px solid var(--border-default);color:var(--text-secondary);';

/**
 * Renders a pill-shaped badge with a colored dot (inline styles for AG Grid cells).
 * Matches the dp-status-badge component visual output.
 */
export function renderBadge(label: string, color: string, pulse = false): string {
  const bg = `color-mix(in srgb, ${color} 12%, transparent)`;
  const dotAnim = pulse ? 'animation:pulse 2s cubic-bezier(.4,0,.6,1) infinite;' : '';
  return `<span style="${BADGE_STYLE}background-color:${bg};color:${color}">` +
    `<span style="${DOT_STYLE}background-color:${color};${dotAnim}"></span>` +
    `${label}</span>`;
}

/**
 * Renders an outlined (ghost) badge without a dot — for neutral/tag-like labels.
 */
export function renderOutlineBadge(label: string): string {
  return `<span style="${OUTLINE_BADGE_STYLE}">${label}</span>`;
}

/**
 * Renders a marketplace badge for AG Grid cells using registry colors.
 * Uses inline styles so it works in raw innerHTML without CSS class dependencies.
 */
export function renderMarketplaceBadge(marketplaceType: string): string {
  const mc = getMarketplaceConfig(marketplaceType as MarketplaceType);
  return `<span style="display:inline-flex;align-items:center;border-radius:4px;` +
    `padding:2px 8px;font-size:11px;font-weight:600;line-height:16px;` +
    `background-color:${mc.badgeBg};color:${mc.badgeText}">${mc.shortLabel}</span>`;
}
