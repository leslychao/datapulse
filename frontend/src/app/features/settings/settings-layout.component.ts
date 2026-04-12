import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import {
  LucideAngularModule,
  LucideIconData,
  Plug,
  Users,
  User,
  MailPlus,
  Building2,
  BellRing,
  ScrollText,
  Bot,
} from 'lucide-angular';

import { WorkspaceRole } from '@core/models';
import { RbacService } from '@core/auth/rbac.service';

interface NavItem {
  path: string;
  labelKey: string;
  icon: LucideIconData;
  visibleRoles?: Set<WorkspaceRole>;
}

const ADMIN_ONLY = new Set<WorkspaceRole>(['ADMIN', 'OWNER']);
const OPERATOR_PLUS = new Set<WorkspaceRole>(['OPERATOR', 'PRICING_MANAGER', 'ADMIN', 'OWNER']);

@Component({
  selector: 'dp-settings-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule, TranslatePipe],
  template: `
    <div class="flex h-full">
      <nav class="w-[200px] shrink-0 border-r border-[var(--border-default)] bg-[var(--bg-secondary)] py-3 px-2">
        <h2 class="px-3 pb-2 text-[var(--text-xs)] font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">
          {{ 'settings.nav.title' | translate }}
        </h2>
        @for (item of visibleNavItems(); track item.path) {
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
export class SettingsLayoutComponent {

  private readonly rbac = inject(RbacService);

  private readonly allNavItems: NavItem[] = [
    { path: 'profile', labelKey: 'settings.nav.profile', icon: User },
    { path: 'general', labelKey: 'settings.nav.general', icon: Building2 },
    { path: 'connections', labelKey: 'settings.nav.connections', icon: Plug },
    { path: 'team', labelKey: 'settings.nav.team', icon: Users },
    { path: 'invitations', labelKey: 'settings.nav.invitations', icon: MailPlus, visibleRoles: ADMIN_ONLY },
    { path: 'bidding', labelKey: 'settings.nav.bidding', icon: Bot, visibleRoles: ADMIN_ONLY },
    { path: 'alert-rules', labelKey: 'settings.nav.alert_rules', icon: BellRing, visibleRoles: OPERATOR_PLUS },
    { path: 'audit', labelKey: 'settings.nav.audit_log', icon: ScrollText, visibleRoles: ADMIN_ONLY },
  ];

  readonly visibleNavItems = computed(() => {
    const role = this.rbac.currentRole();
    return this.allNavItems.filter(
      (item) => !item.visibleRoles || (role != null && item.visibleRoles.has(role)),
    );
  });
}
