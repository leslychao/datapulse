import { ChangeDetectionStrategy, Component, computed, effect, HostListener, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';

import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { NavigationStore } from '@shared/stores/navigation.store';

import { AnalyticsHealthService } from './analytics-health.service';

interface NavTab {
  labelKey: string;
  path: string;
}

interface SubNavLink {
  labelKey: string;
  path: string;
}

const MODULE = 'analytics';
const DEFAULT_TAB = 'pnl/summary';

const SECTION_TABS: NavTab[] = [
  { labelKey: 'analytics.nav.pnl', path: 'pnl' },
  { labelKey: 'analytics.nav.inventory', path: 'inventory' },
  { labelKey: 'analytics.nav.returns', path: 'returns' },
  { labelKey: 'analytics.nav.data_quality', path: 'data-quality' },
];

const SUB_NAV: Record<string, SubNavLink[]> = {
  pnl: [
    { labelKey: 'analytics.subnav.pnl.summary', path: 'pnl/summary' },
    { labelKey: 'analytics.subnav.pnl.by_product', path: 'pnl/by-product' },
    { labelKey: 'analytics.subnav.pnl.by_posting', path: 'pnl/by-posting' },
    { labelKey: 'analytics.subnav.pnl.trend', path: 'pnl/trend' },
  ],
  inventory: [
    { labelKey: 'analytics.subnav.inventory.overview', path: 'inventory/overview' },
    { labelKey: 'analytics.subnav.inventory.by_product', path: 'inventory/by-product' },
  ],
  returns: [
    { labelKey: 'analytics.subnav.returns.overview', path: 'returns/overview' },
    { labelKey: 'analytics.subnav.returns.by_product', path: 'returns/by-product' },
    { labelKey: 'analytics.subnav.returns.reasons', path: 'returns/reasons' },
  ],
  'data-quality': [
    { labelKey: 'analytics.subnav.data_quality.status', path: 'data-quality/status' },
    { labelKey: 'analytics.subnav.data_quality.reconciliation', path: 'data-quality/reconciliation' },
  ],
};

@Component({
  selector: 'dp-analytics-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <div class="flex h-full min-h-0 flex-col">
      @if (healthService.clickhouseUnavailable()) {
        <div class="flex items-center gap-2 border-b border-[var(--status-warning)] bg-[color-mix(in_srgb,var(--status-warning)_8%,transparent)] px-4 py-2 text-[length:var(--text-sm)] text-[var(--status-warning)]">
          <span>⚠</span>
          <span>{{ 'analytics.clickhouse_unavailable' | translate }}</span>
        </div>
      }

      <!-- Section tabs -->
      <div data-tour="analytics-section-tabs"
           class="flex gap-1 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4
                  [&>a:first-child]:pl-0">
        @for (tab of sectionTabs; track tab.path) {
          <a
            [routerLink]="tab.path"
            routerLinkActive="active"
            class="border-b-2 border-transparent px-3 py-2.5 text-[length:var(--text-sm)]
                   font-medium text-[var(--text-secondary)] transition-colors
                   hover:text-[var(--text-primary)]
                   [&.active]:border-[var(--accent-primary)] [&.active]:text-[var(--accent-primary)]"
          >
            {{ tab.labelKey | translate }}
          </a>
        }
      </div>

      <!-- Sub-navigation -->
      @if (subNavLinks().length > 0) {
        <div data-tour="analytics-sub-nav"
             class="flex gap-1 border-b border-[var(--border-default)] px-4
                    [&>a:first-child]:pl-0">
          @for (link of subNavLinks(); track link.path) {
            <a
              [routerLink]="link.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="subNavMatchOptions"
              class="border-b-2 border-transparent px-3 py-2.5 text-[length:var(--text-sm)]
                     font-medium text-[var(--text-secondary)] transition-colors
                     hover:text-[var(--text-primary)]
                     [&.active]:border-[var(--accent-primary)] [&.active]:text-[var(--accent-primary)]"
            >
              {{ link.labelKey | translate }}
            </a>
          }
        </div>
      }

      <!-- Page content -->
      <div class="flex-1 overflow-y-auto min-h-0 p-4">
        <router-outlet />
      </div>
    </div>
  `,
})
export class AnalyticsLayoutComponent {
  readonly healthService = inject(AnalyticsHealthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly navStore = inject(NavigationStore);

  private readonly currentChild = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => this.extractChild(e.urlAfterRedirects)),
    ),
    { initialValue: this.extractChild(this.router.url) },
  );

  constructor() {
    effect(() => {
      const wsId = this.wsStore.currentWorkspaceId();
      if (wsId) {
        this.healthService.checkHealth(wsId);
      }
    });

    const lastTab = this.navStore.getLastTab(MODULE);
    if (lastTab && lastTab !== DEFAULT_TAB && !lastTab.includes('stock-history')) {
      const wsId = this.wsStore.currentWorkspaceId();
      if (wsId) {
        this.router.navigate(
          ['/workspace', String(wsId), MODULE, lastTab],
          { replaceUrl: true },
        );
      }
    }

    effect(() => {
      const child = this.currentChild();
      if (child) this.navStore.setLastTab(MODULE, child);
    });
  }

  readonly sectionTabs = SECTION_TABS;

  readonly subNavMatchOptions = {
    paths: 'exact' as const,
    queryParams: 'ignored' as const,
    matrixParams: 'ignored' as const,
    fragment: 'ignored' as const,
  };

  private static readonly SHORTCUT_ROUTES: Record<string, string> = {
    '1': 'pnl/summary',
    '2': 'inventory/overview',
    '3': 'returns/overview',
    '4': 'data-quality/status',
  };

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const tag = (event.target as HTMLElement)?.tagName;
    if (
      tag === 'INPUT' ||
      tag === 'TEXTAREA' ||
      tag === 'SELECT' ||
      (event.target as HTMLElement)?.isContentEditable
    ) {
      return;
    }

    const route = AnalyticsLayoutComponent.SHORTCUT_ROUTES[event.key];
    if (route) {
      event.preventDefault();
      this.router.navigate([route], { relativeTo: this.route });
    }
  }

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url),
    ),
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

  private extractChild(url: string): string | null {
    const marker = `/${MODULE}/`;
    const idx = url.indexOf(marker);
    if (idx < 0) return null;
    return url.substring(idx + marker.length).split('?')[0] || null;
  }
}
