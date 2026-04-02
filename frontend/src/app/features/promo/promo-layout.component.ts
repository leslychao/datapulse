import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

interface PromoTab {
  label: string;
  path: string;
}

@Component({
  selector: 'dp-promo-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <div class="flex h-full min-h-0 flex-col">
      <div class="flex gap-1 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4
                  [&>a:first-child]:pl-0">
        @for (tab of tabs; track tab.path) {
          <a
            [routerLink]="getTabRoute(tab.path)"
            routerLinkActive="border-[var(--accent-primary)] text-[var(--accent-primary)]"
            [routerLinkActiveOptions]="{ exact: tab.path === 'campaigns' }"
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
export class PromoLayoutComponent {
  private readonly router = inject(Router);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly tabs: PromoTab[] = [
    { label: 'promo.tabs.campaigns', path: 'campaigns' },
    { label: 'promo.tabs.policies', path: 'policies' },
    { label: 'promo.tabs.evaluations', path: 'evaluations' },
    { label: 'promo.tabs.decisions', path: 'decisions' },
  ];

  getTabRoute(path: string): string[] {
    return ['/workspace', String(this.wsStore.currentWorkspaceId()), 'promo', path];
  }
}
