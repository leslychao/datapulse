import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Check } from 'lucide-angular';
import { lastValueFrom } from 'rxjs';

import { WorkspaceApiService } from '@core/api/workspace-api.service';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { WorkspaceDetail } from '@core/models';

@Component({
  selector: 'dp-workspace-switcher',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule, RouterLink, TranslatePipe],
  template: `
    <div class="relative">
      <button
        (click)="toggle()"
        class="flex cursor-pointer items-center justify-center rounded-[var(--radius-md)] p-1 transition-colors hover:bg-[var(--bg-tertiary)]"
        [attr.aria-label]="'shell.workspace_switcher.switch' | translate"
      >
        <div
          class="flex h-7 w-7 items-center justify-center rounded-[var(--radius-md)] bg-[var(--accent-primary)] text-xs font-bold text-white"
        >
          {{ workspaceInitial() }}
        </div>
      </button>

      @if (open()) {
        <div
          class="absolute left-0 top-full z-50 mt-1 flex w-[280px] max-h-[360px] flex-col overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] shadow-[var(--shadow-md)]"
        >
          <div class="border-b border-[var(--border-default)] px-3 py-2">
            <span class="text-sm font-semibold text-[var(--text-primary)]">{{ 'shell.workspace_switcher.title' | translate }}</span>
          </div>

          <div class="flex-1 overflow-y-auto">
            @for (ws of workspaces(); track ws.id) {
              <button
                (click)="selectWorkspace(ws)"
                class="flex w-full cursor-pointer items-center justify-between gap-2 px-3 py-2 text-left transition-colors hover:bg-[var(--bg-tertiary)]"
              >
                <div class="flex flex-col gap-0.5 overflow-hidden">
                  <span class="truncate text-sm font-medium text-[var(--text-primary)]">{{ ws.name }}</span>
                  <span class="text-xs text-[var(--text-tertiary)]">{{ 'shell.workspace_switcher.connections_count' | translate:{ count: ws.connectionsCount } }}</span>
                </div>

                @if (ws.id === currentWorkspaceId()) {
                  <lucide-icon [img]="Check" [size]="16" class="shrink-0 text-[var(--accent-primary)]" />
                }
              </button>
            }
          </div>

          <a
            routerLink="/workspaces"
            (click)="close()"
            class="block border-t border-[var(--border-default)] px-3 py-2 text-xs font-medium text-[var(--accent-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            {{ 'shell.workspace_switcher.manage' | translate }}
          </a>
        </div>
      }
    </div>
  `,
})
export class WorkspaceSwitcherComponent {
  protected readonly Check = Check;

  private readonly store = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);
  private readonly workspaceApi = inject(WorkspaceApiService);

  protected readonly open = signal(false);
  protected readonly loading = signal(false);
  protected readonly workspaces = signal<WorkspaceDetail[]>([]);

  protected readonly currentWorkspaceId = this.store.currentWorkspaceId;

  protected readonly workspaceInitial = computed(() => {
    const name = this.store.currentWorkspaceName();
    return name ? name.charAt(0).toUpperCase() : 'D';
  });

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.close();
    }
  }

  protected toggle(): void {
    const opening = !this.open();
    this.open.set(opening);
    if (opening) {
      this.loadWorkspaces();
    }
  }

  private async loadWorkspaces(): Promise<void> {
    if (this.workspaces().length > 0) return;
    this.loading.set(true);
    try {
      const list = await lastValueFrom(this.workspaceApi.listWorkspaces());
      this.workspaces.set(list);
    } finally {
      this.loading.set(false);
    }
  }

  protected close(): void {
    this.open.set(false);
  }

  protected selectWorkspace(ws: WorkspaceDetail): void {
    this.store.setWorkspace(ws.id, ws.name);
    this.close();
    this.router.navigate(['/workspace', ws.id, 'grid']);
  }
}
