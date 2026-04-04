import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, ShieldAlert } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';

import { AlertApiService } from '@core/api/alert-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

@Component({
  selector: 'dp-automation-blocker-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule, TranslatePipe],
  template: `
    @if (isBlocked()) {
      <div
        class="flex items-center gap-2 bg-[var(--status-error)] px-4 py-1.5 text-sm font-medium text-white"
        role="alert"
      >
        <lucide-icon [img]="ShieldAlert" [size]="16" />
        <span>{{ 'automation_blocker.message' | translate }}</span>
      </div>
    }
  `,
})
export class AutomationBlockerBannerComponent {
  protected readonly ShieldAlert = ShieldAlert;

  private readonly alertApi = inject(AlertApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  protected readonly blockerQuery = injectQuery(() => ({
    queryKey: ['alerts', 'blocker', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.alertApi.getSummary()),
    enabled: !!this.wsStore.currentWorkspaceId(),
    staleTime: 60_000,
  }));

  protected readonly isBlocked = computed(() => {
    const data = this.blockerQuery.data();
    if (!data) return false;
    return data.openCritical > 0;
  });
}
