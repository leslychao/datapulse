import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '@ngx-translate/core';
import { filter, map } from 'rxjs';

import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { NavigationStore } from '@shared/stores/navigation.store';

interface PricingTab {
  label: string;
  path: string;
}

const MODULE = 'pricing';
const DEFAULT_TAB = 'policies';

@Component({
  selector: 'dp-pricing-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <div class="flex h-full min-h-0 flex-col">
      <div data-tour="pricing-tabs"
           class="flex gap-1 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4
                  [&>a:first-child]:pl-0">
        @for (tab of tabs; track tab.path) {
          <a
            [routerLink]="getTabRoute(tab.path)"
            routerLinkActive="border-[var(--accent-primary)] text-[var(--accent-primary)]"
            [routerLinkActiveOptions]="tab.path === 'policies' ? exactPathOptions : { exact: false }"
            class="border-b-2 border-transparent px-3 py-2.5 text-[length:var(--text-sm)]
                   font-medium text-[var(--text-secondary)] transition-colors
                   hover:text-[var(--text-primary)]"
          >
            {{ tab.label | translate }}
          </a>
        }
      </div>
      <div class="flex flex-1 flex-col overflow-hidden min-h-0">
        <router-outlet />
      </div>
    </div>
  `,
})
export class PricingLayoutComponent {
  private readonly router = inject(Router);
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
    const lastTab = this.navStore.getLastTab(MODULE);
    if (lastTab && lastTab !== DEFAULT_TAB) {
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

  readonly exactPathOptions = {
    paths: 'exact' as const,
    queryParams: 'ignored' as const,
    matrixParams: 'ignored' as const,
    fragment: 'ignored' as const,
  };

  readonly tabs: PricingTab[] = [
    { label: 'pricing.tabs.policies', path: 'policies' },
    { label: 'pricing.tabs.runs', path: 'runs' },
    { label: 'pricing.tabs.decisions', path: 'decisions' },
    { label: 'pricing.tabs.price_actions', path: 'price-actions' },
    { label: 'pricing.tabs.simulation', path: 'simulation' },
    { label: 'pricing.tabs.locks', path: 'locks' },
    { label: 'pricing.tabs.competitors', path: 'competitors' },
    { label: 'pricing.tabs.insights', path: 'insights' },
  ];

  getTabRoute(path: string): string[] {
    return ['/workspace', String(this.wsStore.currentWorkspaceId()), MODULE, path];
  }

  private extractChild(url: string): string | null {
    const marker = `/${MODULE}/`;
    const idx = url.indexOf(marker);
    if (idx < 0) return null;
    const rest = url.substring(idx + marker.length).split('?')[0];
    return rest.split('/')[0] || null;
  }
}
