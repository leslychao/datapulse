import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';

import { PromoApiService } from '@core/api/promo-api.service';
import { ConnectionApiService } from '@core/api/connection-api.service';
import {
  CreatePromoAssignmentRequest,
  PromoAssignmentScopeType,
  PromoPolicyAssignment,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { RbacService } from '@core/auth/rbac.service';
import { DataGridComponent } from '@shared/components/data-grid/data-grid.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';


const SCOPE_TYPE_COLOR: Record<PromoAssignmentScopeType, string> = {
  CONNECTION: 'info',
  CATEGORY: 'neutral',
  SKU: 'neutral',
};

@Component({
  selector: 'dp-promo-policy-assignments-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TranslatePipe,
    FormsModule,
    DataGridComponent,
    EmptyStateComponent,
    ConfirmationModalComponent,
  ],
  template: `
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between border-b border-[var(--border-default)] bg-[var(--bg-secondary)] px-4 py-2">
        <div class="flex items-center gap-2">
          <button
            (click)="navigateBack()"
            class="cursor-pointer text-sm text-[var(--text-secondary)] transition-colors hover:text-[var(--text-primary)]"
          >
            ← {{ 'promo.policies.title' | translate }}
          </button>
        </div>
        <h2 class="text-base font-semibold text-[var(--text-primary)]">
          {{ 'promo.assignments.title' | translate }}
        </h2>
      </div>

      <div class="flex-1 px-4 py-2">
        @if (showAddForm()) {
          <div class="mb-4 flex items-end gap-3 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] p-3">
            <div>
              <label class="mb-1 block text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'promo.assignments.scope_type' | translate }}
              </label>
              <select
                [(ngModel)]="newScopeType"
                class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)]
                       bg-[var(--bg-primary)] px-3 text-[length:var(--text-sm)]
                       text-[var(--text-primary)] outline-none"
              >
                <option value="CONNECTION">{{ 'promo.assignments.scope.connection' | translate }}</option>
                <option value="CATEGORY">{{ 'promo.assignments.scope.category' | translate }}</option>
                <option value="SKU">{{ 'promo.assignments.scope.sku' | translate }}</option>
              </select>
            </div>

            <div>
              <label class="mb-1 block text-[length:var(--text-xs)] text-[var(--text-secondary)]">
                {{ 'promo.assignments.connection' | translate }}
              </label>
              <select
                [(ngModel)]="newConnectionId"
                class="h-8 rounded-[var(--radius-md)] border border-[var(--border-default)]
                       bg-[var(--bg-primary)] px-3 text-[length:var(--text-sm)]
                       text-[var(--text-primary)] outline-none"
              >
                <option [ngValue]="null" disabled>{{ 'promo.assignments.connection' | translate }}</option>
                @for (conn of connections(); track conn.id) {
                  <option [ngValue]="conn.id">{{ conn.name }} ({{ conn.marketplaceType }})</option>
                }
              </select>
            </div>

            <button
              (click)="submitAdd()"
              [disabled]="!newConnectionId"
              class="h-8 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {{ 'promo.assignments.add' | translate }}
            </button>
            <button
              (click)="showAddForm.set(false)"
              class="h-8 cursor-pointer rounded-[var(--radius-md)] px-3 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'common.cancel' | translate }}
            </button>
          </div>
        } @else if (rbac.canWritePromo()) {
          <div class="mb-3">
            <button
              (click)="showAddForm.set(true)"
              class="cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
            >
              {{ 'promo.assignments.add_btn' | translate }}
            </button>
          </div>
        }

        @if (assignmentsQuery.isError()) {
          <dp-empty-state
            [message]="'promo.assignments.error' | translate"
            [actionLabel]="'actions.retry' | translate"
            (action)="assignmentsQuery.refetch()"
          />
        } @else if (!assignmentsQuery.isPending() && rows().length === 0) {
          <dp-empty-state
            [message]="'promo.assignments.empty' | translate"
            [actionLabel]="'promo.assignments.add_btn' | translate"
            (action)="showAddForm.set(true)"
          />
        } @else {
          <dp-data-grid
            [columnDefs]="columnDefs()"
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
      [title]="'promo.assignments.delete_title' | translate"
      [message]="'promo.assignments.delete_message' | translate"
      [confirmLabel]="'promo.assignments.delete_confirm' | translate"
      [danger]="true"
      (confirmed)="executeDelete()"
      (cancelled)="showDeleteModal.set(false)"
    />
  `,
})
export class PromoPolicyAssignmentsPageComponent {
  private readonly promoApi = inject(PromoApiService);
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly queryClient = inject(QueryClient);
  private readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly policyId = input.required<string>();

  readonly showAddForm = signal(false);
  readonly showDeleteModal = signal(false);
  readonly deleteTarget = signal<PromoPolicyAssignment | null>(null);
  newScopeType: PromoAssignmentScopeType = 'CONNECTION';
  newConnectionId: number | null = null;

  readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
  }));

  readonly connections = computed(() => this.connectionsQuery.data() ?? []);

  readonly columnDefs = computed(() => {
    const cols: any[] = [
      {
        headerName: this.translate.instant('promo.assignments.col.connection'),
        field: 'connectionName',
        minWidth: 200,
        sortable: true,
      },
      {
        headerName: this.translate.instant('promo.assignments.col.scope_type'),
        field: 'scopeType',
        width: 140,
        cellRenderer: (params: any) => {
          const st = params.value as PromoAssignmentScopeType;
          const label = this.translate.instant(`promo.assignments.scope.${st.toLowerCase()}`);
          const color = SCOPE_TYPE_COLOR[st] ?? 'neutral';
          const cssVar = `var(--status-${color})`;
          return `<span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                    style="background-color: color-mix(in srgb, ${cssVar} 12%, transparent); color: ${cssVar}">
            ${label}
          </span>`;
        },
      },
      {
        headerName: this.translate.instant('promo.assignments.col.scope'),
        field: 'scopeTargetName',
        minWidth: 200,
        valueFormatter: (params: any) =>
          params.value ?? this.translate.instant('promo.assignments.whole_connection'),
      },
    ];
    if (this.rbac.canWritePromo()) {
      cols.push({
        headerName: '',
        field: '_delete',
        width: 60,
        sortable: false,
        cellRenderer: () => {
          const trashIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/><line x1="10" x2="10" y1="11" y2="17"/><line x1="14" x2="14" y1="11" y2="17"/></svg>`;
          return `<button class="action-btn" data-action="delete" title="${this.translate.instant('actions.delete')}">${trashIcon}</button>`;
        },
        onCellClicked: (params: any) => {
          const target = params.event?.target as HTMLElement;
          if (target?.closest('[data-action="delete"]') && params.data) {
            this.deleteTarget.set(params.data);
            this.showDeleteModal.set(true);
          }
        },
      });
    }
    return cols;
  });

  readonly assignmentsQuery = injectQuery(() => ({
    queryKey: ['promo-assignments', this.wsStore.currentWorkspaceId(), this.policyId()],
    queryFn: () =>
      lastValueFrom(
        this.promoApi.listAssignments(
          this.wsStore.currentWorkspaceId()!,
          Number(this.policyId()),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.policyId(),
  }));

  readonly rows = computed(() => this.assignmentsQuery.data() ?? []);

  private readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreatePromoAssignmentRequest) =>
      lastValueFrom(
        this.promoApi.createAssignment(
          this.wsStore.currentWorkspaceId()!,
          Number(this.policyId()),
          req,
        ),
      ),
    onSuccess: () => {
      this.showAddForm.set(false);
      this.newConnectionId = null;
      this.queryClient.invalidateQueries({ queryKey: ['promo-assignments'] });
      this.toast.success(this.translate.instant('promo.assignments.toast.create_success'));
    },
    onError: () => this.toast.error(this.translate.instant('promo.assignments.toast.create_error')),
  }));

  private readonly deleteMutation = injectMutation(() => ({
    mutationFn: (assignmentId: number) =>
      lastValueFrom(
        this.promoApi.deleteAssignment(
          this.wsStore.currentWorkspaceId()!,
          Number(this.policyId()),
          assignmentId,
        ),
      ),
    onSuccess: () => {
      this.showDeleteModal.set(false);
      this.deleteTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['promo-assignments'] });
      this.toast.success(this.translate.instant('promo.assignments.toast.delete_success'));
    },
    onError: () => {
      this.showDeleteModal.set(false);
      this.toast.error(this.translate.instant('promo.assignments.toast.delete_error'));
    },
  }));

  readonly getRowId = (params: any) => String(params.data.id);

  submitAdd(): void {
    if (!this.newConnectionId) return;
    const req: CreatePromoAssignmentRequest = {
      marketplaceConnectionId: this.newConnectionId,
      scopeType: this.newScopeType,
    };
    this.createMutation.mutate(req);
  }

  executeDelete(): void {
    const target = this.deleteTarget();
    if (target) {
      this.deleteMutation.mutate(target.id);
    }
  }

  navigateBack(): void {
    const wsId = this.wsStore.currentWorkspaceId();
    this.router.navigate(['/workspace', wsId, 'promo', 'policies']);
  }
}
