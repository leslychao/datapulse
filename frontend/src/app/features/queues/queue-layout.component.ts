import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
} from '@angular/core';
import { Router, RouterOutlet, ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { QueueApiService } from '@core/api/queue-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';

import { QueueSidebarComponent } from './queue-sidebar.component';

@Component({
  selector: 'dp-queue-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, QueueSidebarComponent],
  template: `
    <div
      class="grid h-full min-h-0 bg-[var(--bg-primary)]"
      [style.grid-template-columns]="'200px 1fr'"
    >
      <dp-queue-sidebar class="min-h-0 border-r border-[var(--border-default)]" />
      <div class="min-h-0 overflow-hidden">
        <router-outlet />
      </div>
    </div>
  `,
})
export class QueueLayoutComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly queueApi = inject(QueueApiService);
  private readonly wsStore = inject(WorkspaceContextStore);

  readonly queuesQuery = injectQuery(() => {
    const wsId = this.wsStore.currentWorkspaceId();
    return {
      queryKey: ['queues', wsId] as const,
      queryFn: () => lastValueFrom(this.queueApi.listQueues(wsId!)),
      enabled: wsId != null && wsId > 0,
    };
  });

  constructor() {
    effect(() => {
      const wsId = this.wsStore.currentWorkspaceId();
      const list = this.queuesQuery.data();
      const pending = this.queuesQuery.isPending();
      if (!wsId || pending || !list?.length) return;
      if (this.route.firstChild) return;

      const firstNonEmpty = list.find((q) => q.totalActiveCount > 0);
      const target = firstNonEmpty ?? list[0];
      void this.router.navigate(
        ['/workspace', wsId, 'queues', target.queueId],
        { replaceUrl: true },
      );
    });
  }
}
