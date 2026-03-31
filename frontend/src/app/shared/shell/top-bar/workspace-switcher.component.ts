import { Component, computed, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, ChevronDown, Check } from 'lucide-angular';

import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { WorkspaceDetail } from '@core/models';

@Component({
  selector: 'dp-workspace-switcher',
  standalone: true,
  imports: [LucideAngularModule, RouterLink],
  template: `
    <div class="relative flex items-center">
      <button
        (click)="toggle()"
        class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] px-2 py-1 transition-colors hover:bg-[var(--bg-tertiary)]"
      >
        <span class="text-sm font-semibold text-[var(--text-primary)]">Datapulse</span>

        @if (workspaceName()) {
          <span class="mx-1 text-[var(--text-tertiary)]">/</span>
          <span class="max-w-[120px] truncate text-sm font-semibold text-[var(--text-primary)]">
            {{ workspaceName() }}
          </span>
        }

        <lucide-icon [img]="ChevronDown" [size]="14" class="text-[var(--text-tertiary)]" />
      </button>

      @if (open()) {
        <div
          class="absolute left-0 top-full z-50 mt-1 flex w-[280px] max-h-[360px] flex-col overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] shadow-[var(--shadow-md)]"
        >
          <div class="border-b border-[var(--border-default)] px-3 py-2">
            <span class="text-sm font-semibold text-[var(--text-primary)]">Рабочие пространства</span>
          </div>

          <div class="flex-1 overflow-y-auto">
            @for (ws of workspaces(); track ws.id) {
              <button
                (click)="selectWorkspace(ws)"
                class="flex w-full cursor-pointer items-center justify-between gap-2 px-3 py-2 text-left transition-colors hover:bg-[var(--bg-tertiary)]"
              >
                <div class="flex flex-col gap-0.5 overflow-hidden">
                  <span class="truncate text-sm font-medium text-[var(--text-primary)]">{{ ws.name }}</span>
                  <span class="text-xs text-[var(--text-tertiary)]">{{ ws.connectionsCount }} подкл.</span>
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
            Управление рабочими пространствами
          </a>
        </div>
      }
    </div>
  `,
})
export class WorkspaceSwitcherComponent {
  protected readonly ChevronDown = ChevronDown;
  protected readonly Check = Check;

  private readonly store = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);

  protected readonly open = signal(false);
  protected readonly workspaces = signal<WorkspaceDetail[]>([]);

  protected readonly currentWorkspaceId = this.store.currentWorkspaceId;

  protected readonly workspaceName = computed(() => {
    const name = this.store.currentWorkspaceName();
    if (!name) return null;
    return name.length > 20 ? name.slice(0, 20) + '…' : name;
  });

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.close();
    }
  }

  protected toggle(): void {
    this.open.update((v) => !v);
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
