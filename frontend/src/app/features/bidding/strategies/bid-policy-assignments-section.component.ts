import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  injectQuery,
  injectMutation,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import {
  LucideAngularModule,
  Trash2,
  Plus,
  Package,
  Megaphone,
} from 'lucide-angular';

import { BiddingApiService } from '@core/api/bidding-api.service';
import { RbacService } from '@core/auth/rbac.service';
import {
  BidPolicyAssignment,
  CreateBidAssignmentRequest,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

type ScopeChoice = 'PRODUCT' | 'CAMPAIGN';

@Component({
  selector: 'dp-bid-policy-assignments-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    ConfirmationModalComponent,
  ],
  host: { class: 'block' },
  template: `
    <section
      id="section-assignments"
      class="scroll-mt-4 rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)] p-6"
    >
      <div class="mb-4 flex items-center justify-between">
        <div>
          <h2 class="text-[var(--text-sm)] font-semibold text-[var(--text-primary)]">
            {{ 'bidding.assignments.title' | translate }}
          </h2>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'bidding.assignments.subtitle' | translate }}
          </p>
        </div>
        @if (rbac.canWritePolicies() && !showAddForm()) {
          <button
            type="button"
            (click)="showAddForm.set(true)"
            class="flex h-8 shrink-0 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 text-[var(--text-xs)] font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            <lucide-icon [img]="icons.Plus" [size]="12" />
            {{ 'bidding.assignments.add_btn' | translate }}
          </button>
        }
      </div>

      <!-- Add form -->
      @if (showAddForm()) {
        <div class="mb-4 rounded-[var(--radius-md)] border border-[var(--accent-primary)]/30 bg-[var(--accent-subtle)]/30 p-4">
          <!-- Scope radio -->
          <div class="mb-4">
            <span class="mb-2 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
              {{ 'bidding.assignments.scope_label' | translate }}
            </span>
            <div class="grid grid-cols-2 gap-2">
              @for (opt of scopeOptions; track opt.value) {
                <label
                  class="relative flex cursor-pointer items-center gap-2.5 rounded-[var(--radius-md)] border p-2.5 transition-all"
                  [class]="formScope() === opt.value
                    ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] shadow-[0_0_0_1px_var(--accent-primary)]'
                    : 'border-[var(--border-default)] bg-[var(--bg-primary)] hover:border-[var(--accent-primary)]/40'"
                >
                  <input
                    type="radio"
                    name="scope"
                    [value]="opt.value"
                    [checked]="formScope() === opt.value"
                    (change)="formScope.set(opt.value)"
                    class="sr-only"
                  />
                  <lucide-icon
                    [img]="opt.icon"
                    [size]="16"
                    [class]="formScope() === opt.value
                      ? 'text-[var(--accent-primary)]'
                      : 'text-[var(--text-tertiary)]'"
                  />
                  <div class="min-w-0 flex-1">
                    <span
                      class="block text-[var(--text-xs)] font-medium"
                      [class]="formScope() === opt.value
                        ? 'text-[var(--accent-primary)]'
                        : 'text-[var(--text-primary)]'"
                    >{{ opt.labelKey | translate }}</span>
                    <span class="block truncate text-[10px] leading-tight text-[var(--text-tertiary)]">
                      {{ opt.descKey | translate }}
                    </span>
                  </div>
                </label>
              }
            </div>
          </div>

          <!-- Product: marketplaceOfferId -->
          @if (formScope() === 'PRODUCT') {
            <div class="mb-3">
              <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
                {{ 'bidding.assignments.offer_label' | translate }}
              </label>
              <input
                type="number"
                [ngModel]="formOfferId()"
                (ngModelChange)="formOfferId.set($event)"
                [placeholder]="'bidding.assignments.offer_placeholder' | translate"
                class="h-8 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-[var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
            </div>
          }

          <!-- Campaign: campaignExternalId -->
          @if (formScope() === 'CAMPAIGN') {
            <div class="mb-3">
              <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
                {{ 'bidding.assignments.campaign_label' | translate }}
              </label>
              <input
                type="text"
                [ngModel]="formCampaignId()"
                (ngModelChange)="formCampaignId.set($event)"
                [placeholder]="'bidding.assignments.campaign_placeholder' | translate"
                class="h-8 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-[var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
              />
            </div>
          }

          <!-- Actions -->
          <div class="flex items-center gap-2">
            <button
              type="button"
              (click)="submitCreate()"
              [disabled]="!isFormValid() || createMutation.isPending()"
              class="h-7 cursor-pointer rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 text-[var(--text-xs)] font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {{ 'bidding.assignments.submit' | translate }}
            </button>
            <button
              type="button"
              (click)="cancelAdd()"
              class="h-7 cursor-pointer rounded-[var(--radius-md)] px-3 text-[var(--text-xs)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'common.cancel' | translate }}
            </button>
          </div>
        </div>
      }

      <!-- Assignments list -->
      @if (assignmentsQuery.isPending()) {
        <div class="space-y-2">
          @for (_ of [1, 2]; track _) {
            <div class="dp-shimmer h-12 rounded-[var(--radius-md)]"></div>
          }
        </div>
      } @else if (assignmentsQuery.isError()) {
        <p class="py-4 text-center text-[var(--text-sm)] text-[var(--status-error)]">
          {{ 'bidding.assignments.create_error' | translate }}
        </p>
      } @else if (!rows().length && !showAddForm()) {
        <div class="flex flex-col items-center py-8 text-center">
          <p class="text-[var(--text-sm)] text-[var(--text-tertiary)]">
            {{ 'bidding.assignments.empty' | translate }}
          </p>
          @if (rbac.canWritePolicies()) {
            <button
              type="button"
              (click)="showAddForm.set(true)"
              class="mt-2 cursor-pointer text-[var(--text-sm)] font-medium text-[var(--accent-primary)] transition-colors hover:text-[var(--accent-primary-hover)]"
            >
              {{ 'bidding.assignments.add_btn' | translate }}
            </button>
          }
        </div>
      } @else {
        <div class="space-y-1.5">
          @for (a of rows(); track a.id) {
            <div class="flex items-center gap-3 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] px-3 py-2">
              <lucide-icon
                [img]="a.scope === 'CAMPAIGN' ? icons.Megaphone : icons.Package"
                [size]="14"
                [class]="a.scope === 'CAMPAIGN' ? 'text-[var(--status-warning)]' : 'text-[var(--status-info)]'"
              />
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="text-[var(--text-sm)] font-medium text-[var(--text-primary)]">
                    {{ a.scope === 'CAMPAIGN' ? a.campaignExternalId : ('#' + a.marketplaceOfferId) }}
                  </span>
                  <span
                    class="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-[10px] font-medium"
                    [class]="a.scope === 'CAMPAIGN'
                      ? 'bg-[var(--status-warning)]/10 text-[var(--status-warning)]'
                      : 'bg-[var(--status-info)]/10 text-[var(--status-info)]'"
                  >
                    {{ ('bidding.assignments.scope.' + a.scope) | translate }}
                  </span>
                </div>
              </div>
              @if (rbac.canWritePolicies()) {
                <button
                  type="button"
                  (click)="confirmDelete(a)"
                  class="shrink-0 cursor-pointer rounded p-1 text-[var(--text-tertiary)] transition-colors hover:bg-[var(--status-error)]/10 hover:text-[var(--status-error)]"
                >
                  <lucide-icon [img]="icons.Trash2" [size]="14" />
                </button>
              }
            </div>
          }
        </div>
      }
    </section>

    <dp-confirmation-modal
      [open]="showDeleteModal()"
      [title]="'bidding.assignments.delete_title' | translate"
      [message]="'bidding.assignments.delete_message' | translate"
      [confirmLabel]="'bidding.assignments.delete_confirm' | translate"
      [danger]="true"
      (confirmed)="executeDelete()"
      (cancelled)="showDeleteModal.set(false)"
    />
  `,
})
export class BidPolicyAssignmentsSectionComponent {
  private readonly biddingApi = inject(BiddingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);
  protected readonly rbac = inject(RbacService);

  readonly policyId = input.required<number>();

  readonly icons = { Trash2, Plus, Package, Megaphone };

  readonly scopeOptions: { value: ScopeChoice; labelKey: string; descKey: string; icon: any }[] = [
    {
      value: 'PRODUCT',
      labelKey: 'bidding.assignments.scope.PRODUCT',
      descKey: 'bidding.assignments.scope_desc.PRODUCT',
      icon: Package,
    },
    {
      value: 'CAMPAIGN',
      labelKey: 'bidding.assignments.scope.CAMPAIGN',
      descKey: 'bidding.assignments.scope_desc.CAMPAIGN',
      icon: Megaphone,
    },
  ];

  readonly showAddForm = signal(false);
  readonly showDeleteModal = signal(false);
  readonly deleteTarget = signal<BidPolicyAssignment | null>(null);

  readonly formScope = signal<ScopeChoice>('PRODUCT');
  readonly formOfferId = signal<number | null>(null);
  readonly formCampaignId = signal<string>('');

  readonly assignmentsQuery = injectQuery(() => ({
    queryKey: ['bid-assignments', this.wsStore.currentWorkspaceId(), this.policyId()],
    queryFn: () =>
      lastValueFrom(
        this.biddingApi.listAssignments(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
        ),
      ),
    enabled: !!this.wsStore.currentWorkspaceId() && !!this.policyId(),
    staleTime: 30_000,
  }));

  readonly rows = computed(() => this.assignmentsQuery.data()?.content ?? []);

  protected readonly createMutation = injectMutation(() => ({
    mutationFn: (req: CreateBidAssignmentRequest) =>
      lastValueFrom(
        this.biddingApi.createAssignment(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
          req,
        ),
      ),
    onSuccess: () => {
      this.resetForm();
      this.queryClient.invalidateQueries({ queryKey: ['bid-assignments'] });
      this.queryClient.invalidateQueries({ queryKey: ['bid-policies'] });
      this.toast.success(this.translate.instant('bidding.assignments.created'));
    },
    onError: () =>
      this.toast.error(this.translate.instant('bidding.assignments.create_error')),
  }));

  private readonly deleteMutation = injectMutation(() => ({
    mutationFn: (assignmentId: number) =>
      lastValueFrom(
        this.biddingApi.deleteAssignment(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
          assignmentId,
        ),
      ),
    onSuccess: () => {
      this.showDeleteModal.set(false);
      this.deleteTarget.set(null);
      this.queryClient.invalidateQueries({ queryKey: ['bid-assignments'] });
      this.queryClient.invalidateQueries({ queryKey: ['bid-policies'] });
      this.toast.success(this.translate.instant('bidding.assignments.deleted'));
    },
    onError: () => {
      this.showDeleteModal.set(false);
      this.toast.error(this.translate.instant('bidding.assignments.delete_error'));
    },
  }));

  isFormValid(): boolean {
    if (this.formScope() === 'PRODUCT') {
      return this.formOfferId() != null && this.formOfferId()! > 0;
    }
    return this.formCampaignId().trim().length > 0;
  }

  submitCreate(): void {
    if (!this.isFormValid()) return;
    const scope = this.formScope();
    const req: CreateBidAssignmentRequest = { scope };
    if (scope === 'PRODUCT') {
      req.marketplaceOfferId = this.formOfferId()!;
    } else {
      req.campaignExternalId = this.formCampaignId().trim();
    }
    this.createMutation.mutate(req);
  }

  cancelAdd(): void {
    this.resetForm();
  }

  confirmDelete(assignment: BidPolicyAssignment): void {
    this.deleteTarget.set(assignment);
    this.showDeleteModal.set(true);
  }

  executeDelete(): void {
    const target = this.deleteTarget();
    if (target) this.deleteMutation.mutate(target.id);
  }

  private resetForm(): void {
    this.showAddForm.set(false);
    this.formScope.set('PRODUCT');
    this.formOfferId.set(null);
    this.formCampaignId.set('');
  }
}
