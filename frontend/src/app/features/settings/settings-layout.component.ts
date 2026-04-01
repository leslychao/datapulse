import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
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
} from 'lucide-angular';

interface NavItem {
  path: string;
  label: string;
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
          Настройки
        </h2>
        @for (item of navItems; track item.path) {
          <a [routerLink]="item.path"
             routerLinkActive="active-nav"
             class="nav-item flex items-center gap-2 rounded-[var(--radius-md)] px-3 py-1.5 text-[var(--text-sm)]
                    text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)] transition-colors duration-[var(--transition-fast)]">
            <lucide-icon [img]="item.icon" [size]="16" />
            {{ item.label }}
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
  readonly navItems: NavItem[] = [
    { path: 'general', label: 'Общие', icon: Settings },
    { path: 'connections', label: 'Подключения', icon: Plug },
    { path: 'cost-profiles', label: 'Себестоимость', icon: Calculator },
    { path: 'team', label: 'Команда', icon: Users },
    { path: 'invitations', label: 'Приглашения', icon: Mail },
    { path: 'alert-rules', label: 'Правила алертов', icon: BellRing },
    { path: 'audit', label: 'Журнал аудита', icon: ScrollText },
  ];
}
