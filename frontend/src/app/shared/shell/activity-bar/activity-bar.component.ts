import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {
  LucideAngularModule,
  LucideIconData,
  LayoutGrid,
  Package,
  BarChart3,
  Tag,
  Gift,
  Megaphone,
  ArrowLeftRight,
  ListTodo,
  Bell,
  Settings,
} from 'lucide-angular';
import { TranslateService } from '@ngx-translate/core';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

interface NavItem {
  icon: LucideIconData;
  route: string;
  tooltipKey: string;
}

@Component({
  selector: 'dp-activity-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    <nav class="flex h-full w-12 flex-col items-center bg-[var(--bg-secondary)] py-3"
         role="navigation"
         aria-label="Main navigation">
      @for (item of topItems; track item.route) {
        <a [routerLink]="getRoute(item.route)"
           routerLinkActive="activity-bar-active"
           class="group relative flex h-10 w-10 items-center justify-center
                  rounded-[var(--radius-md)] text-[var(--text-secondary)]
                  transition-colors duration-[var(--transition-fast)]
                  hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
           [attr.aria-label]="t(item.tooltipKey)"
           role="tab">
          <lucide-icon [img]="item.icon" [size]="20" />
          <span class="pointer-events-none absolute left-14 z-50 hidden whitespace-nowrap
                       rounded-[var(--radius-md)] bg-[var(--bg-primary)] px-2 py-1
                       text-[13px] text-[var(--text-primary)] shadow-[var(--shadow-md)]
                       group-hover:block">
            {{ t(item.tooltipKey) }}
          </span>
        </a>
      }

      <div class="my-2 w-8 border-t border-[var(--border-default)]"></div>

      <a [routerLink]="getRoute(settingsItem.route)"
         routerLinkActive="activity-bar-active"
         class="group relative mt-auto flex h-10 w-10 items-center justify-center
                rounded-[var(--radius-md)] text-[var(--text-secondary)]
                transition-colors duration-[var(--transition-fast)]
                hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
         [attr.aria-label]="t(settingsItem.tooltipKey)"
         role="tab">
        <lucide-icon [img]="settingsItem.icon" [size]="20" />
        <span class="pointer-events-none absolute left-14 z-50 hidden whitespace-nowrap
                     rounded-[var(--radius-md)] bg-[var(--bg-primary)] px-2 py-1
                     text-[13px] text-[var(--text-primary)] shadow-[var(--shadow-md)]
                     group-hover:block">
          {{ t(settingsItem.tooltipKey) }}
        </span>
      </a>
    </nav>
  `,
  styles: [`
    :host ::ng-deep .activity-bar-active {
      background-color: var(--accent-subtle);
      color: var(--accent-primary);
    }
    :host ::ng-deep .activity-bar-active::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 2px;
      height: 20px;
      border-radius: 0 1px 1px 0;
      background-color: var(--accent-primary);
    }
  `],
})
export class ActivityBarComponent {
  private readonly workspaceStore = inject(WorkspaceContextStore);
  private readonly translate = inject(TranslateService);

  readonly topItems: NavItem[] = [
    { icon: LayoutGrid, route: 'grid', tooltipKey: 'shell.nav.operations' },
    { icon: Package, route: 'catalog', tooltipKey: 'shell.nav.catalog' },
    { icon: BarChart3, route: 'analytics', tooltipKey: 'shell.nav.analytics' },
    { icon: Tag, route: 'pricing', tooltipKey: 'shell.nav.pricing' },
    { icon: Gift, route: 'promo', tooltipKey: 'shell.nav.promo' },
    { icon: Megaphone, route: 'advertising', tooltipKey: 'shell.nav.advertising' },
    { icon: ArrowLeftRight, route: 'mismatches', tooltipKey: 'shell.nav.mismatches' },
    { icon: ListTodo, route: 'queues', tooltipKey: 'shell.nav.queues' },
    { icon: Bell, route: 'alerts', tooltipKey: 'shell.nav.alerts' },
  ];

  readonly settingsItem: NavItem = {
    icon: Settings,
    route: 'settings',
    tooltipKey: 'shell.nav.settings',
  };

  t(key: string): string {
    return this.translate.instant(key);
  }

  getRoute(segment: string): string[] {
    const wsId = this.workspaceStore.currentWorkspaceId();
    return ['/workspace', String(wsId), segment];
  }
}
