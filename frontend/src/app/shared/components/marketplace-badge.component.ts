import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { MarketplaceType, getMarketplaceConfig } from '@core/models';

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
  readonly type = input.required<string>();

  protected readonly config = computed(() => {
    const mc = getMarketplaceConfig(this.type() as MarketplaceType);
    return { bg: mc.badgeBg, text: mc.badgeText, label: mc.shortLabel };
  });
}
