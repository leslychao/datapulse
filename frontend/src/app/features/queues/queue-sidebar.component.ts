import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { LucideAngularModule, Check, Plus, Pencil } from 'lucide-angular';
import { lastValueFrom } from 'rxjs';

import { QueueApiService } from '@core/api/queue-api.service';
import { Queue, QueueType } from '@core/models';
import { RbacService } from '@core/auth/rbac.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { QueueStore } from '@shared/stores/queue.store';

import { QueueBuilderModalComponent } from './queue-builder-modal.component';

const DOT_COLORS: Record<QueueType, string> = {
  ATTENTION: 'bg-[var(--status-error)]',
  DECISION: 'bg-[var(--status-warning)]',
  PROCESSING: 'bg-[var(--status-info)]',
};

@Component({
  selector: 'dp-queue-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
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
                class="dp-queue-nav-item flex h-9 items-center gap-2 rounded-[var(--radius-md)] px-2 text-sm text-[var(--text-primary)]"
                (click)="onSelect(q)"
              >
                <span
                  class="h-1.5 w-1.5 shrink-0 rounded-full"
                  [class]="dotColor(q.queueType)"
                ></span>
                <span class="min-w-0 flex-1 truncate">{{ q.name }}</span>
                @if (q.totalActiveCount === 0) {
                  <lucide-icon [img]="checkIcon" [size]="14" class="text-[var(--status-success)]" />
                } @else {
                  <span
                    class="rounded-full bg-[var(--bg-tertiary)] px-1.5 py-0.5 text-xs tabular-nums text-[var(--text-secondary)]"
                  >
                    {{ q.totalActiveCount | number }}
                  </span>
                }
              </a>
            </li>
          }
        </ul>
      </div>

      <div class="flex-1 overflow-y-auto px-3 py-2.5">
        <div class="flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wide text-[var(--text-tertiary)]">
            {{ 'queues.sidebar.custom' | translate }}
          </span>
          @if (rbac.canOperateActions()) {
            <button
              type="button"
              class="flex h-5 w-5 items-center justify-center rounded-[var(--radius-sm)] text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--accent-primary)]"
              [attr.aria-label]="'queues.sidebar.new_queue' | translate"
              (click)="openCreate()"
            >
              <lucide-icon [img]="plusIcon" [size]="14" />
            </button>
          }
        </div>
        <ul class="mt-2 space-y-0.5">
          @for (q of customQueues(); track q.queueId) {
            <li class="group">
              <a
                [routerLink]="['/workspace', workspaceId(), 'queues', q.queueId]"
                routerLinkActive="dp-queue-nav-active"
                class="dp-queue-nav-item flex h-9 items-center gap-2 rounded-[var(--radius-md)] px-2 text-sm text-[var(--text-primary)]"
                (click)="onSelect(q)"
              >
                <span
                  class="h-1.5 w-1.5 shrink-0 rounded-full border border-[var(--text-tertiary)]"
                ></span>
                <span class="min-w-0 flex-1 truncate">{{ q.name }}</span>
                @if (rbac.canOperateActions()) {
                  <button
                    type="button"
                    class="hidden h-5 w-5 shrink-0 items-center justify-center rounded-[var(--radius-sm)] text-[var(--text-tertiary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--accent-primary)] group-hover:flex"
                    [attr.aria-label]="'queues.sidebar.edit_queue' | translate"
                    (click)="openEdit(q, $event)"
                  >
                    <lucide-icon [img]="pencilIcon" [size]="12" />
                  </button>
                }
                @if (q.totalActiveCount === 0) {
                  <lucide-icon [img]="checkIcon" [size]="14" class="text-[var(--status-success)]" />
                } @else {
                  <span
                    class="rounded-full bg-[var(--bg-tertiary)] px-1.5 py-0.5 text-xs tabular-nums text-[var(--text-secondary)]"
                  >
                    {{ q.totalActiveCount | number }}
                  </span>
                }
              </a>
            </li>
          }
        </ul>
      </div>

      @if (rbac.canOperateActions()) {
        <div class="border-t border-[var(--border-default)] px-3 py-2">
          <button
            type="button"
            class="flex w-full items-center justify-center gap-1.5 rounded-[var(--radius-md)] px-3 py-1.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
            (click)="openCreate()"
          >
            <lucide-icon [img]="plusIcon" [size]="14" />
            {{ 'queues.sidebar.new_queue' | translate }}
          </button>
        </div>
      }

      <dp-queue-builder-modal
        [open]="builderOpen()"
        [editQueue]="editingQueue()"
        (openChange)="onModalClose($event)"
        (saved)="onQueueSaved($event)"
      />
    </div>
  `,
  styles: [
    `
      :host ::ng-deep .dp-queue-nav-active {
        background-color: var(--bg-active);
        box-shadow: inset 2px 0 0 var(--accent-primary);
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
  protected readonly rbac = inject(RbacService);

  readonly plusIcon = Plus;
  readonly checkIcon = Check;
  readonly pencilIcon = Pencil;

  readonly builderOpen = signal(false);
  readonly editingQueue = signal<Queue | null>(null);

  readonly workspaceId = this.wsStore.currentWorkspaceId;

  readonly queuesQuery = injectQuery(() => {
    const wsId = this.wsStore.currentWorkspaceId();
    return {
      queryKey: ['queues', wsId] as const,
      queryFn: () => lastValueFrom(this.queueApi.listQueues(wsId!)),
      enabled: wsId != null && wsId > 0,
    };
  });

  readonly systemQueues = computed(() =>
    (this.queuesQuery.data() ?? []).filter((q) => q.isSystem === true),
  );

  readonly customQueues = computed(() =>
    (this.queuesQuery.data() ?? []).filter((q) => q.isSystem !== true),
  );

  dotColor(type: QueueType): string {
    return DOT_COLORS[type] ?? 'bg-[var(--text-tertiary)]';
  }

  onSelect(q: Queue): void {
    this.queueStore.selectQueue(q.queueId);
  }

  openCreate(): void {
    this.editingQueue.set(null);
    this.builderOpen.set(true);
  }

  openEdit(q: Queue, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.editingQueue.set(q);
    this.builderOpen.set(true);
  }

  onModalClose(open: boolean): void {
    this.builderOpen.set(open);
    if (!open) this.editingQueue.set(null);
  }

  onQueueSaved(queueId: number): void {
    void this.queuesQuery.refetch();
    const wsId = this.wsStore.currentWorkspaceId();
    if (wsId) {
      void this.router.navigate(['/workspace', wsId, 'queues', queueId]);
    }
  }
}
