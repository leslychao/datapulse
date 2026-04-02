import { ChangeDetectionStrategy, Component, output } from '@angular/core';

import { WorkspaceSwitcherComponent } from './workspace-switcher.component';
import { BreadcrumbsComponent } from './breadcrumbs.component';
import { SearchTriggerComponent } from './search-trigger.component';
import { NotificationBellComponent } from './notification-bell.component';
import { UserMenuComponent } from './user-menu.component';

@Component({
  selector: 'dp-top-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WorkspaceSwitcherComponent,
    BreadcrumbsComponent,
    SearchTriggerComponent,
    NotificationBellComponent,
    UserMenuComponent,
  ],
  template: `
    <header
      class="grid h-10 shrink-0 items-center border-b border-[var(--border-default)] bg-[var(--bg-primary)]"
      style="grid-template-columns: 48px 1fr auto"
    >
      <dp-workspace-switcher
        class="flex h-full items-center justify-center border-r border-[var(--border-default)]" />

      <dp-breadcrumbs class="overflow-hidden px-3" />

      <div class="flex items-center gap-2 px-3">
        <dp-search-trigger (searchRequested)="searchRequested.emit()" />
        <dp-notification-bell />
        <dp-user-menu />
      </div>
    </header>
  `,
})
export class TopBarComponent {
  readonly searchRequested = output();
}
