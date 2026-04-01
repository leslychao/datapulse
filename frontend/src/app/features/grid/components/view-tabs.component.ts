import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectMutation, injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, Lock } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { ViewApiService } from '@core/api/view-api.service';
import { GridView } from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { GridStore } from '@shared/stores/grid.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { FormModalComponent } from '@shared/components/form-modal.component';

@Component({
  selector: 'dp-view-tabs',
  standalone: true,
  imports: [LucideAngularModule, TranslatePipe, FormModalComponent, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center border-b border-[var(--border-default)] px-4">
      <div class="flex items-center gap-0.5 overflow-x-auto">
        @for (view of viewsQuery.data() ?? []; track view.id) {
          <button
            (click)="selectView(view)"
            class="relative flex cursor-pointer items-center gap-1 whitespace-nowrap px-3 py-2 text-[length:var(--text-sm)] transition-colors"
            [class]="view.id === gridStore.selectedViewId()
              ? 'font-semibold text-[var(--text-primary)]'
              : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
          >
            @if (view.isSystem) {
              <lucide-icon [img]="LockIcon" [size]="12" class="text-[var(--text-tertiary)]"></lucide-icon>
            }
            {{ view.name }}
            @if (view.id === gridStore.selectedViewId()) {
              <span class="absolute bottom-0 left-0 right-0 h-0.5 bg-[var(--accent-primary)]"></span>
            }
          </button>
        }
      </div>

      <button
        (click)="showCreateModal.set(true)"
        class="ml-1 flex cursor-pointer items-center gap-1 rounded-[var(--radius-sm)] px-2 py-1.5 text-[length:var(--text-sm)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]"
        [attr.aria-label]="'grid.views.create' | translate"
      >
        <lucide-icon [img]="PlusIcon" [size]="14"></lucide-icon>
      </button>
    </div>

    <dp-form-modal
      [title]="'grid.views.create_title' | translate"
      [isOpen]="showCreateModal()"
      [submitLabel]="'actions.save' | translate"
      [submitDisabled]="!newViewName().trim()"
      [isPending]="createMutation.isPending()"
      (submit)="createView()"
      (close)="showCreateModal.set(false)"
    >
      <div class="flex flex-col gap-3">
        <label class="text-[length:var(--text-sm)] text-[var(--text-secondary)]">
          {{ 'grid.views.name_label' | translate }}
        </label>
        <input
          type="text"
          [(ngModel)]="newViewName"
          [placeholder]="'grid.views.name_placeholder' | translate"
          class="h-8 w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 text-[length:var(--text-base)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
        />
      </div>
    </dp-form-modal>
  `,
})
export class ViewTabsComponent {
  private readonly viewApi = inject(ViewApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  protected readonly gridStore = inject(GridStore);

  protected readonly PlusIcon = Plus;
  protected readonly LockIcon = Lock;

  protected readonly showCreateModal = signal(false);
  protected readonly newViewName = signal('');

  readonly viewsQuery = injectQuery(() => ({
    queryKey: ['views', this.wsStore.currentWorkspaceId()],
    queryFn: () => lastValueFrom(this.viewApi.listViews(this.wsStore.currentWorkspaceId()!)),
    enabled: !!this.wsStore.currentWorkspaceId(),
  }));

  readonly createMutation = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(this.viewApi.createView(this.wsStore.currentWorkspaceId()!, {
        name: this.newViewName().trim(),
        filters: this.gridStore.filters(),
        sortColumn: this.gridStore.sortColumn() ?? undefined,
        sortDirection: this.gridStore.sortDirection(),
      })),
    onSuccess: () => {
      this.showCreateModal.set(false);
      this.newViewName.set('');
      this.viewsQuery.refetch();
    },
    onError: () => this.toast.error(this.translate.instant('common.error')),
  }));

  selectView(view: GridView): void {
    this.gridStore.setView(
      view.id,
      view.filters ?? {},
      view.sortColumn,
      view.sortDirection ?? 'ASC',
    );
  }

  createView(): void {
    if (!this.newViewName().trim()) return;
    this.createMutation.mutate();
  }
}
