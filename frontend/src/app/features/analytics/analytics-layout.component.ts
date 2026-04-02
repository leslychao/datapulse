import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

interface NavTab {
  labelKey: string;
  path: string;
}

interface SubNavLink {
  labelKey: string;
  path: string;
  exact: boolean;
}

const SECTION_TABS: NavTab[] = [
  { labelKey: 'analytics.nav.pnl', path: 'pnl' },
  { labelKey: 'analytics.nav.inventory', path: 'inventory' },
  { labelKey: 'analytics.nav.returns', path: 'returns' },
  { labelKey: 'analytics.nav.data_quality', path: 'data-quality' },
];

const SUB_NAV: Record<string, SubNavLink[]> = {
  pnl: [
    { labelKey: 'analytics.subnav.pnl.summary', path: 'pnl', exact: true },
    { labelKey: 'analytics.subnav.pnl.by_product', path: 'pnl/by-product', exact: true },
    { labelKey: 'analytics.subnav.pnl.by_posting', path: 'pnl/by-posting', exact: true },
    { labelKey: 'analytics.subnav.pnl.trend', path: 'pnl/trend', exact: true },
  ],
  inventory: [
    { labelKey: 'analytics.subnav.inventory.overview', path: 'inventory', exact: true },
    { labelKey: 'analytics.subnav.inventory.by_product', path: 'inventory/by-product', exact: true },
    { labelKey: 'analytics.subnav.inventory.history', path: 'inventory/stock-history', exact: true },
  ],
  returns: [
    { labelKey: 'analytics.subnav.returns.summary', path: 'returns', exact: true },
    { labelKey: 'analytics.subnav.returns.by_product', path: 'returns/by-product', exact: true },
    { labelKey: 'analytics.subnav.returns.trend', path: 'returns/trend', exact: true },
  ],
  'data-quality': [
    { labelKey: 'analytics.subnav.data_quality.status', path: 'data-quality', exact: true },
    { labelKey: 'analytics.subnav.data_quality.reconciliation', path: 'data-quality/reconciliation', exact: true },
  ],
};

@Component({
  selector: 'dp-analytics-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <div class="flex h-full min-h-0 flex-col">
      <!-- Section tabs -->
      <div class="flex gap-1 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-6
                  [&>a:first-child]:pl-0">
        @for (tab of sectionTabs; track tab.path) {
          <a
            [routerLink]="tab.path"
            routerLinkActive="border-[var(--accent-primary)] text-[var(--accent-primary)]"
            class="border-b-2 border-transparent px-3 py-2.5 text-[length:var(--text-sm)]
                   font-medium text-[var(--text-secondary)] transition-colors
                   hover:text-[var(--text-primary)]"
          >
            {{ tab.labelKey | translate }}
          </a>
        }
      </div>

      <!-- Sub-navigation -->
      @if (subNavLinks().length > 0) {
        <div class="flex gap-1 px-6 py-2 [&>a:first-child]:ml-[-0.75rem]">
          @for (link of subNavLinks(); track link.path) {
            <a
              [routerLink]="link.path"
              routerLinkActive="bg-[var(--accent-subtle)] text-[var(--accent-primary)] font-medium"
              [routerLinkActiveOptions]="{ exact: link.exact }"
              class="rounded-[var(--radius-md)] px-3 py-1.5 text-[length:var(--text-sm)]
                     text-[var(--text-secondary)] transition-colors
                     hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
            >
              {{ link.labelKey | translate }}
            </a>
          }
        </div>
      }

      <!-- Page content -->
      <div class="flex-1 overflow-y-auto min-h-0 p-6">
        <router-outlet />
      </div>
    </div>
  `,
})
export class AnalyticsLayoutComponent {
  private readonly router = inject(Router);

  readonly sectionTabs = SECTION_TABS;

  private readonly url = toSignal(
    this.router.events.pipe(map(() => this.router.url)),
    { initialValue: this.router.url },
  );

  readonly subNavLinks = computed<SubNavLink[]>(() => {
    const url = this.url();
    for (const section of Object.keys(SUB_NAV)) {
      if (url.includes(`/analytics/${section}`)) {
        return SUB_NAV[section];
      }
    }
    return SUB_NAV['pnl'];
  });
}
