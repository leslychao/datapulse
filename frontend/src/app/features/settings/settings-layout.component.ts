import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import {
  LucideAngularModule,
  LucideIconData,
  Plug,
  Users,
  Mail,
  Settings,
  Calculator,
  BellRing,
  ScrollText,
  RefreshCw,
} from 'lucide-angular';

interface NavItem {
  path: string;
  labelKey: string;
  icon: LucideIconData;
}

@Component({
  selector: 'dp-settings-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    <div class="flex h-full">
      <nav class="w-52 shrink-0 border-r border-[var(--border-default)] bg-[var(--bg-secondary)] py-3 px-2">
        <h2 class="px-3 pb-2 text-[var(--text-xs)] font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">
          {{ t('settings.nav.title') }}
        </h2>
        @for (item of navItems; track item.path) {
          <a [routerLink]="item.path"
             routerLinkActive="active-nav"
             class="nav-item flex items-center gap-2 rounded-[var(--radius-md)] px-3 py-1.5 text-[var(--text-sm)]
                    text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)] transition-colors duration-[var(--transition-fast)]">
            <lucide-icon [img]="item.icon" [size]="16" />
            {{ t(item.labelKey) }}
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
    }
  `],
})
export class SettingsLayoutComponent {
  private readonly translate = inject(TranslateService);

  readonly navItems: NavItem[] = [
    { path: 'general', labelKey: 'settings.nav.general', icon: Settings },
    { path: 'connections', labelKey: 'settings.nav.connections', icon: Plug },
    { path: 'cost-profiles', labelKey: 'settings.nav.cost_profiles', icon: Calculator },
    { path: 'team', labelKey: 'settings.nav.team', icon: Users },
    { path: 'invitations', labelKey: 'settings.nav.invitations', icon: Mail },
    { path: 'jobs', labelKey: 'settings.nav.jobs', icon: RefreshCw },
    { path: 'alert-rules', labelKey: 'settings.nav.alert_rules', icon: BellRing },
    { path: 'audit', labelKey: 'settings.nav.audit_log', icon: ScrollText },
  ];

  t(key: string): string {
    return this.translate.instant(key);
  }
}
