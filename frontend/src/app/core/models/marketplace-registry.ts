import { MarketplaceType } from './connection.model';

export interface MarketplaceConfig {
  type: MarketplaceType;
  label: string;
  shortLabel: string;
  badgeBg: string;
  badgeText: string;
  credentialFields: CredentialFieldDef[];
}

export interface CredentialFieldDef {
  key: string;
  labelKey: string;
  inputType: 'text' | 'textarea' | 'password';
  placeholderKey?: string;
  hintKey?: string;
}

export const MARKETPLACE_REGISTRY: MarketplaceConfig[] = [
  {
    type: 'WB',
    label: 'Wildberries',
    shortLabel: 'WB',
    badgeBg: 'var(--mp-wb)',
    badgeText: 'var(--mp-wb-text)',
    credentialFields: [
      {
        key: 'apiToken',
        labelKey: 'marketplace.credentials.wb_token',
        inputType: 'textarea',
        placeholderKey: 'marketplace.credentials.wb_token_placeholder',
        hintKey: 'marketplace.credentials.wb_token_hint',
      },
    ],
  },
  {
    type: 'OZON',
    label: 'Ozon',
    shortLabel: 'Ozon',
    badgeBg: 'var(--mp-ozon)',
    badgeText: 'var(--mp-ozon-text)',
    credentialFields: [
      {
        key: 'clientId',
        labelKey: 'marketplace.credentials.ozon_client_id',
        inputType: 'text',
        placeholderKey: 'marketplace.credentials.ozon_client_id_placeholder',
        hintKey: 'marketplace.credentials.ozon_client_id_hint',
      },
      {
        key: 'apiKey',
        labelKey: 'marketplace.credentials.ozon_api_key',
        inputType: 'password',
        placeholderKey: 'marketplace.credentials.ozon_api_key_placeholder',
      },
    ],
  },
  {
    type: 'YANDEX',
    label: 'Яндекс.Маркет',
    shortLabel: 'YM',
    badgeBg: 'var(--mp-yandex)',
    badgeText: 'var(--mp-yandex-text)',
    credentialFields: [
      {
        key: 'apiKey',
        labelKey: 'marketplace.credentials.yandex_api_key',
        inputType: 'text',
        placeholderKey: 'marketplace.credentials.yandex_api_key_placeholder',
        hintKey: 'marketplace.credentials.yandex_api_key_hint',
      },
    ],
  },
];

export function getMarketplaceConfig(type: MarketplaceType): MarketplaceConfig {
  const upper = (type ?? '').toUpperCase() as MarketplaceType;
  return MARKETPLACE_REGISTRY.find(m => m.type === upper) ?? {
    type: upper,
    label: upper,
    shortLabel: upper,
    badgeBg: 'var(--bg-tertiary)',
    badgeText: 'var(--text-secondary)',
    credentialFields: [],
  };
}

export function getMarketplaceLabel(type: MarketplaceType): string {
  return getMarketplaceConfig(type).label;
}

export function getMarketplaceShortLabel(type: string): string {
  const upper = (type ?? '').toUpperCase();
  const cfg = MARKETPLACE_REGISTRY.find(m => m.type === upper);
  return cfg ? cfg.shortLabel : type;
}
