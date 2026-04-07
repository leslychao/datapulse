import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import {
  LucideAngularModule,
  LucideIconData,
  DollarSign,
} from 'lucide-angular';

import { RbacService } from '@core/auth/rbac.service';

interface NavItem {
  path: string;
  labelKey: string;
  icon: LucideIconData;
}

@Component({
  selector: 'dp-catalog-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule, TranslatePipe],
  template: `
    <div class="flex h-full">
      <nav class="w-[200px] shrink-0 border-r border-[var(--border-default)] bg-[var(--bg-secondary)] py-3 px-2">
        <h2 class="px-3 pb-2 text-[var(--text-xs)] font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">
          {{ 'catalog.nav.title' | translate }}
        </h2>
        @for (item of navItems; track item.path) {
          <a [routerLink]="item.path"
             routerLinkActive="active-nav"
             class="nav-item flex items-center gap-2 rounded-[var(--radius-md)] px-3 py-1.5 text-[var(--text-sm)]
                    text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)] transition-colors duration-[var(--transition-fast)]">
            <lucide-icon [img]="item.icon" [size]="16" />
            {{ item.labelKey | translate }}
          </a>
        }
      </nav>
      <div class="flex-1 overflow-auto p-6">
        <router-outlet />
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; }
    .active-nav {
      background-color: var(--bg-active);
      color: var(--accent-primary);
      font-weight: 500;
      border-left: 2px solid var(--accent-primary);
    }
  `],
})
export class CatalogLayoutComponent {

  private readonly rbac = inject(RbacService);

  readonly navItems: NavItem[] = [
    { path: 'cost', labelKey: 'catalog.nav.cost', icon: DollarSign },
  ];
}
