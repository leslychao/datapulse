import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

interface NavTab {
  label: string;
  path: string;
}

interface SubNavLink {
  label: string;
  path: string;
  exact: boolean;
}

const SECTION_TABS: NavTab[] = [
  { label: 'P&L', path: 'pnl' },
  { label: 'Остатки', path: 'inventory' },
  { label: 'Возвраты', path: 'returns' },
  { label: 'Качество данных', path: 'data-quality' },
];

const SUB_NAV: Record<string, SubNavLink[]> = {
  pnl: [
    { label: 'Сводка', path: 'pnl', exact: true },
    { label: 'По товарам', path: 'pnl/by-product', exact: true },
    { label: 'По отправкам', path: 'pnl/by-posting', exact: true },
    { label: 'Тренд', path: 'pnl/trend', exact: true },
  ],
  inventory: [
    { label: 'Обзор', path: 'inventory', exact: true },
    { label: 'По товарам', path: 'inventory/by-product', exact: true },
    { label: 'История', path: 'inventory/stock-history', exact: true },
  ],
  returns: [
    { label: 'Сводка', path: 'returns', exact: true },
    { label: 'По товарам', path: 'returns/by-product', exact: true },
    { label: 'Тренд', path: 'returns/trend', exact: true },
  ],
  'data-quality': [
    { label: 'Статус', path: 'data-quality', exact: true },
    { label: 'Reconciliation', path: 'data-quality/reconciliation', exact: true },
  ],
};

@Component({
  selector: 'dp-analytics-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <div class="flex h-full flex-col">
      <!-- Section tabs -->
      <div class="flex gap-1 border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4">
        @for (tab of sectionTabs; track tab.path) {
          <a
            [routerLink]="tab.path"
            routerLinkActive="border-[var(--accent-primary)] text-[var(--accent-primary)]"
            class="border-b-2 border-transparent px-4 py-2.5 text-[length:var(--text-sm)]
                   font-medium text-[var(--text-secondary)] transition-colors
                   hover:text-[var(--text-primary)]"
          >
            {{ tab.label }}
          </a>
        }
      </div>

      <!-- Sub-navigation -->
      @if (subNavLinks().length > 0) {
        <div class="flex gap-1 px-4 py-2">
          @for (link of subNavLinks(); track link.path) {
            <a
              [routerLink]="link.path"
              routerLinkActive="bg-[var(--accent-subtle)] text-[var(--accent-primary)] font-medium"
              [routerLinkActiveOptions]="{ exact: link.exact }"
              class="rounded-[var(--radius-md)] px-3 py-1.5 text-[length:var(--text-sm)]
                     text-[var(--text-secondary)] transition-colors
                     hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
            >
              {{ link.label }}
            </a>
          }
        </div>
      }

      <!-- Page content -->
      <div class="flex-1 overflow-y-auto p-6">
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
