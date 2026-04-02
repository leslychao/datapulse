import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { LucideAngularModule, X, Pin } from 'lucide-angular';

import { TabStore, TabItem } from '@shared/stores/tab.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

@Component({
  selector: 'dp-tab-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    @if (sortedTabs().length > 0) {
      <div class="flex h-9 items-end gap-0 overflow-x-auto border-b border-[var(--border-default)]
                  bg-[var(--bg-secondary)] px-1"
           role="tablist">
        @for (tab of sortedTabs(); track tab.id) {
          <a [routerLink]="tabRoute(tab)"
             routerLinkActive="tab-active"
             class="group relative flex h-[32px] max-w-[180px] items-center gap-1.5 border-b-2
                    border-transparent px-3 text-[13px] text-[var(--text-secondary)]
                    transition-colors duration-[var(--transition-fast)]
                    hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
             [class.border-b-[var(--accent-primary)]]="isActiveTab(tab)"
             role="tab"
             [attr.aria-selected]="isActiveTab(tab)"
             (click)="activateTab(tab)">
            @if (tab.pinned) {
              <lucide-icon [img]="pinIcon" [size]="12"
                           class="shrink-0 text-[var(--text-tertiary)]" />
            }
            <span class="truncate">{{ tab.label }}</span>
            @if (tab.closeable && !tab.pinned) {
              <button class="ml-1 hidden shrink-0 rounded-[var(--radius-sm)] p-0.5
                            text-[var(--text-tertiary)] hover:bg-[var(--bg-tertiary)]
                            hover:text-[var(--text-primary)] group-hover:block"
                      (click)="closeTab($event, tab)"
                      [attr.aria-label]="'Закрыть ' + tab.label">
                <lucide-icon [img]="closeIcon" [size]="12" />
              </button>
            }
          </a>
        }
      </div>
    }
  `,
  styles: [`
    :host ::ng-deep .tab-active {
      background-color: var(--bg-primary);
      color: var(--text-primary);
      border-bottom-color: var(--accent-primary);
    }
  `],
})
export class TabBarComponent {
  private readonly tabStore = inject(TabStore);
  private readonly workspaceStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);

  readonly closeIcon = X;
  readonly pinIcon = Pin;

  readonly currentModule = computed(() => {
    const url = this.router.url;
    const match = url.match(/\/workspace\/\d+\/(\w+)/);
    return match ? match[1] : 'grid';
  });

  readonly sortedTabs = computed(() => {
    const module = this.currentModule();
    const tabs = this.tabStore.getTabs(module);
    const pinned = tabs.filter((t) => t.pinned);
    const regular = tabs.filter((t) => !t.pinned);
    return [...pinned, ...regular];
  });

  isActiveTab(tab: TabItem): boolean {
    const module = this.currentModule();
    return this.tabStore.getActiveTab(module) === tab.id;
  }

  activateTab(tab: TabItem): void {
    const module = this.currentModule();
    this.tabStore.setActiveTab(module, tab.id);
  }

  closeTab(event: Event, tab: TabItem): void {
    event.preventDefault();
    event.stopPropagation();
    const module = this.currentModule();
    this.tabStore.removeTab(module, tab.id);
  }

  tabRoute(tab: TabItem): string[] {
    const wsId = this.workspaceStore.currentWorkspaceId();
    return ['/workspace', String(wsId), ...tab.route.split('/')];
  }
}
