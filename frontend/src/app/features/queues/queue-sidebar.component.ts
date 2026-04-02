import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule, Check, Plus } from 'lucide-angular';
import { lastValueFrom } from 'rxjs';

import { QueueApiService } from '@core/api/queue-api.service';
import { Queue } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { QueueStore } from '@shared/stores/queue.store';

import { QueueBuilderModalComponent } from './queue-builder-modal.component';

@Component({
  selector: 'dp-queue-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    RouterLinkActive,
    TranslatePipe,
    LucideAngularModule,
    QueueBuilderModalComponent,
  ],
  template: `
    <div class="flex h-full flex-col bg-[var(--bg-secondary)]">
      <div class="border-b border-[var(--border-default)] px-3 py-2.5">
        <span class="text-xs font-semibold uppercase tracking-wide text-[var(--text-tertiary)]">
          {{ 'queues.sidebar.system' | translate }}
        </span>
        <ul class="mt-2 space-y-0.5">
          @for (q of systemQueues(); track q.queueId) {
            <li>
              <a
                [routerLink]="['/workspace', workspaceId(), 'queues', q.queueId]"
                routerLinkActive="dp-queue-nav-active"
                class="dp-queue-nav-item flex items-center gap-2 rounded-[var(--radius-md)] px-2 py-1.5 text-sm text-[var(--text-primary)]"
                (click)="onSelect(q)"
              >
                <span class="h-2 w-2 shrink-0 rounded-full" [class]="dotClass(q)"></span>
                <span class="min-w-0 flex-1 truncate">{{ q.name }}</span>
                @if (q.totalActiveCount === 0) {
                  <lucide-icon [img]="checkIcon" [size]="14" class="text-[var(--status-success)]" />
                } @else {
                  <span
                    class="rounded-full bg-[var(--bg-tertiary)] px-1.5 py-0.5 text-xs tabular-nums text-[var(--text-secondary)]"
                  >
                    {{ q.totalActiveCount }}
                  </span>
                }
              </a>
            </li>
          }
        </ul>
      </div>

      <div class="flex-1 overflow-y-auto px-3 py-2.5">
        <span class="text-xs font-semibold uppercase tracking-wide text-[var(--text-tertiary)]">
          {{ 'queues.sidebar.custom' | translate }}
        </span>
        <ul class="mt-2 space-y-0.5">
          @for (q of customQueues(); track q.queueId) {
            <li>
              <a
                [routerLink]="['/workspace', workspaceId(), 'queues', q.queueId]"
                routerLinkActive="dp-queue-nav-active"
                class="dp-queue-nav-item flex items-center gap-2 rounded-[var(--radius-md)] px-2 py-1.5 text-sm text-[var(--text-primary)]"
                (click)="onSelect(q)"
              >
                <span class="h-2 w-2 shrink-0 rounded-full" [class]="dotClass(q)"></span>
                <span class="min-w-0 flex-1 truncate">{{ q.name }}</span>
                @if (q.totalActiveCount === 0) {
                  <lucide-icon [img]="checkIcon" [size]="14" class="text-[var(--status-success)]" />
                } @else {
                  <span
                    class="rounded-full bg-[var(--bg-tertiary)] px-1.5 py-0.5 text-xs tabular-nums text-[var(--text-secondary)]"
                  >
                    {{ q.totalActiveCount }}
                  </span>
                }
              </a>
            </li>
          }
        </ul>
      </div>

      <div class="border-t border-[var(--border-default)] p-2">
        <button
          type="button"
          class="flex w-full items-center justify-center gap-1.5 rounded-[var(--radius-md)] py-1.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--accent-primary)]"
          (click)="builderOpen.set(true)"
        >
          <lucide-icon [img]="plusIcon" [size]="16" />
          {{ 'queues.sidebar.new_queue' | translate }}
        </button>
      </div>

      <dp-queue-builder-modal
        [open]="builderOpen()"
        (openChange)="builderOpen.set($event)"
        (saved)="onQueueSaved($event)"
      />
    </div>
  `,
  styles: [
    `
      :host ::ng-deep .dp-queue-nav-active {
        background-color: var(--bg-active);
        box-shadow: inset 3px 0 0 var(--accent-primary);
      }
      .dp-queue-nav-item:not(.dp-queue-nav-active):hover {
        background-color: var(--bg-tertiary);
      }
    `,
  ],
})
export class QueueSidebarComponent {
  private readonly queueApi = inject(QueueApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly queueStore = inject(QueueStore);
  private readonly router = inject(Router);

  readonly plusIcon = Plus;
  readonly checkIcon = Check;

  readonly builderOpen = signal(false);

  readonly workspaceId = this.wsStore.currentWorkspaceId;

  readonly queuesQuery = injectQuery(() => {
    const wsId = this.wsStore.currentWorkspaceId();
    return {
      queryKey: ['queues', wsId] as const,
      queryFn: () => lastValueFrom(this.queueApi.listQueues(wsId!)),
      enabled: wsId != null && wsId > 0,
    };
  });

  systemQueues(): Queue[] {
    return (this.queuesQuery.data() ?? []).filter((q) => q.isSystem === true);
  }

  customQueues(): Queue[] {
    return (this.queuesQuery.data() ?? []).filter((q) => q.isSystem !== true);
  }

  dotClass(q: Queue): string {
    switch (q.queueType) {
      case 'ATTENTION':
        return 'bg-[var(--status-warning)]';
      case 'DECISION':
        return 'bg-[var(--status-info)]';
      case 'PROCESSING':
        return 'bg-[var(--accent-primary)]';
      default:
        return 'bg-[var(--text-tertiary)]';
    }
  }

  onSelect(q: Queue): void {
    this.queueStore.selectQueue(q.queueId);
  }

  onQueueSaved(queueId: number): void {
    void this.queuesQuery.refetch();
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      void this.router.navigate(['/workspace', wsId, 'queues', queueId]);
    }
  }
}
