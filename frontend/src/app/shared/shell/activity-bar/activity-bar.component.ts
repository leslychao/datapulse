import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {
  LucideAngularModule,
  LucideIconData,
  LayoutGrid,
  BarChart3,
  Tag,
  Gift,
  PlayCircle,
  Settings,
} from 'lucide-angular';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

interface NavItem {
  icon: LucideIconData;
  route: string;
  tooltip: string;
}

@Component({
  selector: 'dp-activity-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    <nav class="flex h-full w-12 flex-col items-center bg-[var(--bg-secondary)] py-3"
         role="navigation"
         aria-label="Основная навигация">
      @for (item of topItems; track item.route) {
        <a [routerLink]="getRoute(item.route)"
           routerLinkActive="activity-bar-active"
           class="group relative flex h-10 w-10 items-center justify-center
                  rounded-[var(--radius-md)] text-[var(--text-secondary)]
                  transition-colors duration-[var(--transition-fast)]
                  hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
           [attr.aria-label]="item.tooltip"
           role="tab">
          <lucide-icon [img]="item.icon" [size]="20" />
          <span class="pointer-events-none absolute left-14 z-50 hidden whitespace-nowrap
                       rounded-[var(--radius-md)] bg-[var(--bg-primary)] px-2 py-1
                       text-[13px] text-[var(--text-primary)] shadow-[var(--shadow-md)]
                       group-hover:block">
            {{ item.tooltip }}
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
         [attr.aria-label]="settingsItem.tooltip"
         role="tab">
        <lucide-icon [img]="settingsItem.icon" [size]="20" />
        <span class="pointer-events-none absolute left-14 z-50 hidden whitespace-nowrap
                     rounded-[var(--radius-md)] bg-[var(--bg-primary)] px-2 py-1
                     text-[13px] text-[var(--text-primary)] shadow-[var(--shadow-md)]
                     group-hover:block">
          {{ settingsItem.tooltip }}
        </span>
      </a>
    </nav>
  `,
  styles: [`
    :host ::ng-deep .activity-bar-active {
      background-color: var(--accent-subtle);
      color: var(--accent-primary);
      box-shadow: inset 2px 0 0 var(--accent-primary);
    }
  `],
})
export class ActivityBarComponent {
  private readonly workspaceStore = inject(WorkspaceContextStore);

  readonly topItems: NavItem[] = [
    { icon: LayoutGrid, route: 'grid', tooltip: 'Операции' },
    { icon: BarChart3, route: 'analytics', tooltip: 'Аналитика' },
    { icon: Tag, route: 'pricing', tooltip: 'Ценообразование' },
    { icon: Gift, route: 'promo', tooltip: 'Промо' },
    { icon: PlayCircle, route: 'execution', tooltip: 'Действия' },
  ];

  readonly settingsItem: NavItem = {
    icon: Settings,
    route: 'settings',
    tooltip: 'Настройки',
  };

  getRoute(segment: string): string[] {
    const wsId = this.workspaceStore.currentWorkspaceId();
    return ['/workspace', String(wsId), segment];
  }
}
