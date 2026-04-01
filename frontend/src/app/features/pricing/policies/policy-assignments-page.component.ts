import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PricingApiService } from '@core/api/pricing-api.service';
import {
  AssignmentScopeType,
  CreateAssignmentRequest,
  PolicyAssignment,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

const SCOPE_TYPE_LABEL: Record<string, string> = {
  CONNECTION: 'Подключение',
  CATEGORY: 'Категория',
  SKU: 'Товар',
};

const SCOPE_TYPE_COLOR: Record<string, string> = {
  CONNECTION: 'info',
  CATEGORY: 'warning',
  SKU: 'success',
};

@Component({
  selector: 'dp-policy-assignments-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    DataGridComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <!-- Toolbar -->
      <div
        class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-3"
      >
        <div class="flex items-center gap-3">
          <button
            (click)="navigateBack()"
            class="cursor-pointer rounded-[var(--radius-md)] px-2 py-1 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
          {{ 'pricing.assignments.back_short' | translate }}
        </button>
          <h2 class="text-base font-semibold text-[var(--text-primary)]">
            {{ 'pricing.assignments.title' | translate }}
          </h2>
        </div>
        <button
          (click)="showCreateForm.set(true)"
          class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
        >
          {{ 'pricing.assignments.add' | translate }}
        </button>
      </div>

      <!-- Create Form Panel -->
      @if (showCreateForm()) {
        <div
          class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-4"
        >
          <div class="flex flex-wrap items-end gap-4">
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.assignments.form.connection_id_label' | translate }}
              </label>
              <input
                type="number"
                [(ngModel)]="formConnectionId"
                class="h-8 w-36 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                placeholder="1"
              />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-[11px] text-[var(--text-tertiary)]">
                {{ 'pricing.assignments.form.scope_label' | translate }}
              </label>
              <div class="flex h-8 items-center gap-3">
                @for (opt of scopeOptions; track opt.value) {
                  <label class="flex cursor-pointer items-center gap-1.5 text-sm text-[var(--text-primary)]">
                    <input
                      type="radio"
                      name="scopeType"
                      [value]="opt.value"
                      [(ngModel)]="formScopeType"
                      class="accent-[var(--accent-primary)]"
                    />
                    {{ opt.labelKey | translate }}
                  </label>
                }
              </div>
            </div>
            @if (formScopeType === 'CATEGORY') {
              <div class="flex flex-col gap-1">
                <label class="text-[11px] text-[var(--text-tertiary)]">
                  {{ 'pricing.assignments.form.category_id_label' | translate }}
                </label>
                <input
                  type="number"
                  [(ngModel)]="formCategoryId"
                  class="h-8 w-36 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  placeholder="42"
                />
              </div>
            }
            @if (formScopeType === 'SKU') {
              <div class="flex flex-col gap-1">
                <label class="text-[11px] text-[var(--text-tertiary)]">
                  {{ 'pricing.assignments.form.offer_id_label' | translate }}
                </label>
                <input
                  type="number"
                  [(ngModel)]="formMarketplaceOfferId"
                  class="h-8 w-36 rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                  placeholder="12345"
                />
              </div>
            }
            <div class="flex gap-2">
              <button
                (click)="submitCreate()"
                [disabled]="!isFormValid()"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {{ 'pricing.assignments.form.submit' | translate }}
              </button>
              <button
                (click)="cancelCreate()"
                class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
              >
                {{ 'actions.cancel' | translate }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Data Grid -->
      <div class="flex-1 px-4 py-3">
        @if (assignmentsQuery.isError()) {
          <dp-empty-state
            [message]="'pricing.assignments.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="assignmentsQuery.refetch()"
          />
        } @else if (!assignmentsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="'pricing.assignments.empty' | translate"
            [actionLabel]="'pricing.assignments.add' | translate"
            (action)="showCreateForm.set(true)"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs"
            [rowData]="rows()"
            [loading]="assignmentsQuery.isPending()"
            [pagination]="false"
            [getRowId]="getRowId"
            [height]="'100%'"
          />
        }
      </div>
    </div>

    <dp-confirmation-modal
      [open]="showDeleteModal()"
      [title]="'pricing.assignments.delete_title' | translate"
      [message]="'pricing.assignments.delete_message' | translate"
      [confirmLabel]="'actions.delete' | translate"
      [danger]="true"
      (confirmed)="executeDelete()"
      (cancelled)="showDeleteModal.set(false)"
    />
  `,
})
export class PolicyAssignmentsPageComponent {
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);

  readonly policyId = input.required<number>();

  readonly showCreateForm = signal(false);
  readonly showDeleteModal = signal(false);
  readonly deleteTarget = signal<PolicyAssignment | null>(null);

  formConnectionId: number | null = null;
  formScopeType: AssignmentScopeType = 'CONNECTION';
  formCategoryId: number | null = null;
  formMarketplaceOfferId: number | null = null;

  readonly scopeOptions = [
    { value: 'CONNECTION' as const, labelKey: 'pricing.assignments.scope.CONNECTION' },
    { value: 'CATEGORY' as const, labelKey: 'pricing.assignments.scope.CATEGORY' },
    { value: 'SKU' as const, labelKey: 'pricing.assignments.scope.SKU' },
  ];

  readonly columnDefs = [
    {
      headerName: 'Область',
      field: 'scopeType',
      width: 150,
      sortable: true,
      cellRenderer: (params: any) => {
        const val = params.value as string;
        const label = SCOPE_TYPE_LABEL[val] ?? val;
        const color = SCOPE_TYPE_COLOR[val] ?? 'neutral';
        const cssVar = `var(--status-${color})`;
        return `<span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
          <span class="inline-block h-1.5 w-1.5 rounded-full" style="background-color: ${cssVar}"></span>
          ${label}
        </span>`;
      },
    },
    {
      headerName: 'Подключение',
      field: 'connectionName',
      minWidth: 200,
      flex: 1,
      sortable: true,
    },
    {
      headerName: 'Цель',
      field: 'target',
      minWidth: 250,
      flex: 1,
      sortable: false,
      valueGetter: (params: any) => {
        if (!params.data) return '';
        const a = params.data as PolicyAssignment;
        if (a.scopeType === 'CONNECTION') return '—';
        if (a.scopeType === 'CATEGORY') return a.categoryName ?? '—';
        return a.offerName
          ? `${a.offerName} (${a.sellerSku})`
          : a.sellerSku ?? '—';
      },
    },
    {
      headerName: '',
      field: 'actions',
      width: 60,
      sortable: false,
      suppressMovable: true,
      cellRenderer: () =>
        `<button class="action-btn" data-action="delete" title="Удалить">🗑</button>`,
      onCellClicked: (params: any) => {
        const target = params.event?.target as HTMLElement;
        const action = target
          ?.closest('[data-action]')
          ?.getAttribute('data-action');
        if (action === 'delete' && params.data) {
          this.deleteTarget.set(params.data);
          this.showDeleteModal.set(true);
        }
      },
    },
  ];

  readonly assignmentsQuery = injectQuery(() => ({
    queryKey: [
      'assignments',
      this.wsStore.currentWorkspaceId(),
      this.policyId(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listAssignments(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.policyId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.assignmentsQuery.data() ?? []);

  private readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreateAssignmentRequest) =>
      lastValueFrom(
        this.pricingApi.createAssignment(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
          req,
        ),
      ),
    onSuccess: () => {
      this.resetForm();
      this.queryClient.invalidateQueries({ queryKey: ['assignments'] });
      this.toast.success(this.translate.instant('pricing.assignments.created'));
    },
    onError: () => this.toast.error(this.translate.instant('pricing.assignments.create_error')),
  }));

  private readonly deleteMutation = injectMutation(() => ({
    mutationFn: (assignmentId: number) =>
      lastValueFrom(
        this.pricingApi.deleteAssignment(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
          assignmentId,
        ),
      ),
    onSuccess: () => {
      this.showDeleteModal.set(false);
      this.deleteTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['assignments'] });
      this.toast.success(this.translate.instant('pricing.assignments.deleted'));
    },
    onError: () => {
      this.showDeleteModal.set(false);
      this.toast.error(this.translate.instant('pricing.assignments.delete_error'));
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  isFormValid(): boolean {
    if (this.formConnectionId === null) return false;
    if (this.formScopeType === 'CATEGORY' && this.formCategoryId === null)
      return false;
    if (
      this.formScopeType === 'SKU' &&
      this.formMarketplaceOfferId === null
    )
      return false;
    return true;
  }

  submitCreate(): void {
    if (!this.isFormValid()) return;
    const req: CreateAssignmentRequest = {
      connectionId: this.formConnectionId!,
      scopeType: this.formScopeType,
    };
    if (this.formScopeType === 'CATEGORY') {
      req.categoryId = this.formCategoryId!;
    }
    if (this.formScopeType === 'SKU') {
      req.marketplaceOfferId = this.formMarketplaceOfferId!;
    }
    this.createMutation.mutate(req);
  }

  cancelCreate(): void {
    this.resetForm();
  }

  executeDelete(): void {
    const target = this.deleteTarget();
    if (target) {
      this.deleteMutation.mutate(target.id);
    }
  }

  navigateBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'pricing', 'policies']);
  }

  private resetForm(): void {
    this.showCreateForm.set(false);
    this.formConnectionId = null;
    this.formScopeType = 'CONNECTION';
    this.formCategoryId = null;
    this.formMarketplaceOfferId = null;
  }
}
