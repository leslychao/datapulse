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
      class="grid h-10 shrink-0 items-center border-b border-[var(--border-default)] bg-[var(--bg-primary)] px-3"
      style="grid-template-columns: 240px 1fr auto"
    >
      <dp-workspace-switcher />

      <dp-breadcrumbs />

      <div class="flex items-center gap-2">
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
