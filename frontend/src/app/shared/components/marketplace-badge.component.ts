import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { MarketplaceType } from '@core/models';

const STYLES: Record<MarketplaceType, { bg: string; text: string; label: string }> = {
  WB: { bg: '#7B2FBE', text: '#FFFFFF', label: 'WB' },
  OZON: { bg: '#005BFF', text: '#FFFFFF', label: 'Ozon' },
};

@Component({
  selector: 'dp-marketplace-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="inline-flex items-center rounded px-2 py-0.5 text-[11px] font-semibold leading-4"
      [style.background-color]="config().bg"
      [style.color]="config().text"
    >
      {{ config().label }}
    </span>
  `,
})
export class MarketplaceBadgeComponent {
  readonly type = input.required<MarketplaceType>();

  protected readonly config = computed(() => STYLES[this.type()]);
}
