import { Component, computed, inject } from '@angular/core';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';

import { SyncStatusStore, SyncHealth } from '@shared/stores/sync-status.store';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { AuthService } from '@core/auth/auth.service';

const HEALTH_COLORS: Record<SyncHealth, string> = {
  OK: 'var(--status-success)',
  STALE: 'var(--status-warning)',
  ERROR: 'var(--status-error)',
};

@Component({
  selector: 'dp-status-bar',
  standalone: true,
  template: `
    <div class="grid h-6 items-center bg-[var(--bg-secondary)] px-3 text-[11px] text-[var(--text-tertiary)]"
         style="grid-template-columns: auto 1fr auto;">
      <!-- Left: sync status dots -->
      <div class="flex items-center gap-3">
        @for (conn of connections(); track conn.connectionId) {
          <div class="flex items-center gap-1">
            <span class="inline-block h-1.5 w-1.5 rounded-full"
                  [style.background-color]="healthColor(conn.status)">
            </span>
            <span>{{ conn.connectionName }}</span>
            @if (conn.lastSuccessAt) {
              <span class="text-[var(--text-tertiary)]">
                {{ relativeTime(conn.lastSuccessAt) }}
              </span>
            }
          </div>
        }
      </div>

      <!-- Center: workspace name -->
      <div class="text-center">
        {{ workspaceName() }}
      </div>

      <!-- Right: user email -->
      <div class="text-right">
        {{ userEmail() }}
      </div>
    </div>
  `,
})
export class StatusBarComponent {
  private readonly syncStore = inject(SyncStatusStore);
  private readonly workspaceStore = inject(WorkspaceContextStore);
  private readonly authService = inject(AuthService);

  readonly connections = this.syncStore.connections;

  readonly workspaceName = computed(
    () => this.workspaceStore.currentWorkspaceName() ?? '',
  );

  readonly userEmail = computed(() => {
    const user = this.authService.user();
    return user?.email ?? '';
  });

  healthColor(status: SyncHealth): string {
    return HEALTH_COLORS[status];
  }

  relativeTime(isoDate: string): string {
    try {
      return formatDistanceToNow(new Date(isoDate), { locale: ru, addSuffix: true });
    } catch {
      return '';
    }
  }
}
