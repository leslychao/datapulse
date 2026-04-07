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
import { lastValueFrom, Subject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  LucideAngularModule,
  Globe,
  FolderTree,
  Package,
  Trash2,
  Plus,
  X,
  Search,
} from 'lucide-angular';

import { ConnectionApiService } from '@core/api/connection-api.service';
import { PricingApiService } from '@core/api/pricing-api.service';
import { RbacService } from '@core/auth/rbac.service';
import {
  AssignmentScopeType,
  CategorySuggestion,
  ConnectionSummary,
  CreateAssignmentRequest,
  OfferSuggestion,
  PolicyAssignment,
} from '@core/models';
import { WorkspaceContextStore } from '@shared/stores/workspace-context.store';
import { ToastService } from '@shared/shell/toast/toast.service';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';

interface ScopeOption {
  value: AssignmentScopeType;
  labelKey: string;
  descKey: string;
  icon: any;
}

@Component({
  selector: 'dp-policy-assignments-section',
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
            {{ 'pricing.form.section.assignments' | translate }}
          </h2>
          <p class="mt-0.5 text-[var(--text-xs)] text-[var(--text-tertiary)]">
            {{ 'pricing.form.assignments.subtitle' | translate }}
          </p>
        </div>
        @if (rbac.canWritePolicies() && !showAddForm()) {
          <button
            type="button"
            (click)="showAddForm.set(true)"
            class="flex h-8 shrink-0 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-3 text-[var(--text-xs)] font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            <lucide-icon [name]="icons.Plus" [size]="12" />
            {{ 'pricing.form.assignments.add_btn' | translate }}
          </button>
        }
      </div>

      <!-- Add form -->
      @if (showAddForm()) {
        <div class="mb-4 rounded-[var(--radius-md)] border border-[var(--accent-primary)]/30 bg-[var(--accent-subtle)]/30 p-4">
          <!-- Scope cards -->
          <div class="mb-4">
            <span class="mb-2 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
              {{ 'pricing.form.assignments.scope_label' | translate }}
            </span>
            <div class="grid grid-cols-3 gap-2">
              @for (opt of scopeOptions; track opt.value) {
                <label
                  class="relative flex cursor-pointer items-center gap-2.5 rounded-[var(--radius-md)] border p-2.5 transition-all"
                  [class]="formScopeType() === opt.value
                    ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] shadow-[0_0_0_1px_var(--accent-primary)]'
                    : 'border-[var(--border-default)] bg-[var(--bg-primary)] hover:border-[var(--accent-primary)]/40'"
                >
                  <input
                    type="radio"
                    name="scopeType"
                    [value]="opt.value"
                    [checked]="formScopeType() === opt.value"
                    (change)="onScopeChange(opt.value)"
                    class="sr-only"
                  />
                  <lucide-icon
                    [name]="opt.icon"
                    [size]="16"
                    [class]="formScopeType() === opt.value
                      ? 'text-[var(--accent-primary)]'
                      : 'text-[var(--text-tertiary)]'"
                  />
                  <div class="min-w-0 flex-1">
                    <span
                      class="block text-[var(--text-xs)] font-medium"
                      [class]="formScopeType() === opt.value
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

          <!-- Connection dropdown -->
          <div class="mb-3">
            <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
              {{ 'pricing.form.assignments.connection_label' | translate }}
            </label>
            <select
              [ngModel]="formConnectionId()"
              (ngModelChange)="onConnectionChange($event)"
              class="h-8 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-[var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            >
              <option [ngValue]="null" disabled>
                {{ 'pricing.form.assignments.connection_placeholder' | translate }}
              </option>
              @for (conn of activeConnections(); track conn.id) {
                <option [ngValue]="conn.id">
                  {{ conn.name }} ({{ conn.marketplaceType }})
                </option>
              }
            </select>
          </div>

          <!-- Category selector -->
          @if (formScopeType() === 'CATEGORY' && formConnectionId()) {
            <div class="mb-3">
              <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
                {{ 'pricing.form.assignments.category_label' | translate }}
              </label>
              @if (categoriesQuery.isPending()) {
                <div class="flex h-8 items-center px-2 text-[var(--text-xs)] text-[var(--text-tertiary)]">
                  {{ 'common.loading' | translate }}
                </div>
              } @else {
                <select
                  [ngModel]="formCategoryId()"
                  (ngModelChange)="formCategoryId.set($event)"
                  class="h-8 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-2 text-[var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                >
                  <option [ngValue]="null" disabled>
                    {{ 'pricing.form.assignments.category_placeholder' | translate }}
                  </option>
                  @for (cat of categoriesQuery.data() ?? []; track cat.id) {
                    <option [ngValue]="cat.id">{{ cat.name }}</option>
                  }
                </select>
              }
            </div>
          }

          <!-- Offer search -->
          @if (formScopeType() === 'SKU' && formConnectionId()) {
            <div class="mb-3">
              <label class="mb-1 block text-[var(--text-xs)] font-medium text-[var(--text-secondary)]">
                {{ 'pricing.form.assignments.offer_label' | translate }}
              </label>
              <div class="relative">
                <lucide-icon
                  [name]="icons.Search"
                  [size]="14"
                  class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--text-tertiary)]"
                />
                <input
                  type="text"
                  [ngModel]="offerSearchText()"
                  (ngModelChange)="onOfferSearch($event)"
                  [placeholder]="'pricing.form.assignments.offer_search_placeholder' | translate"
                  class="h-8 w-full rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)] pl-8 pr-2 text-[var(--text-sm)] text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
                />
              </div>
              @if (offerResults().length) {
                <div class="mt-1 max-h-40 overflow-y-auto rounded-[var(--radius-sm)] border border-[var(--border-default)] bg-[var(--bg-primary)]">
                  @for (offer of offerResults(); track offer.id) {
                    <button
                      type="button"
                      (click)="selectOffer(offer)"
                      class="flex w-full cursor-pointer items-center gap-2 px-3 py-1.5 text-left text-[var(--text-sm)] transition-colors hover:bg-[var(--bg-secondary)]"
                      [class]="selectedOffer()?.id === offer.id ? 'bg-[var(--accent-subtle)]' : ''"
                    >
                      <span class="min-w-0 flex-1 truncate text-[var(--text-primary)]">{{ offer.name }}</span>
                      <span class="shrink-0 font-mono text-[var(--text-xs)] text-[var(--text-tertiary)]">{{ offer.sellerSku }}</span>
                    </button>
                  }
                </div>
              }
              @if (selectedOffer()) {
                <div class="mt-1.5 flex items-center gap-2 rounded-[var(--radius-sm)] bg-[var(--accent-subtle)] px-3 py-1.5">
                  <span class="min-w-0 flex-1 truncate text-[var(--text-sm)] text-[var(--text-primary)]">
                    {{ selectedOffer()!.name }}
                  </span>
                  <span class="font-mono text-[var(--text-xs)] text-[var(--text-tertiary)]">{{ selectedOffer()!.sellerSku }}</span>
                  <button type="button" (click)="clearOffer()" class="cursor-pointer text-[var(--text-tertiary)] hover:text-[var(--text-primary)]">
                    <lucide-icon [name]="icons.X" [size]="14" />
                  </button>
                </div>
              }
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
              {{ 'pricing.form.assignments.submit' | translate }}
            </button>
            <button
              type="button"
              (click)="cancelAdd()"
              class="h-7 cursor-pointer rounded-[var(--radius-md)] px-3 text-[var(--text-xs)] text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
            >
              {{ 'actions.cancel' | translate }}
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
          {{ 'pricing.assignments.error' | translate }}
        </p>
      } @else if (!rows().length && !showAddForm()) {
        <div class="flex flex-col items-center py-8 text-center">
          <p class="text-[var(--text-sm)] text-[var(--text-tertiary)]">
            {{ 'pricing.form.assignments.empty' | translate }}
          </p>
          @if (rbac.canWritePolicies()) {
            <button
              type="button"
              (click)="showAddForm.set(true)"
              class="mt-2 cursor-pointer text-[var(--text-sm)] font-medium text-[var(--accent-primary)] transition-colors hover:text-[var(--accent-primary-hover)]"
            >
              {{ 'pricing.form.assignments.add_btn' | translate }}
            </button>
          }
        </div>
      } @else {
        <div class="space-y-1.5">
          @for (a of rows(); track a.id) {
            <div class="flex items-center gap-3 rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-secondary)] px-3 py-2">
              <lucide-icon
                [name]="scopeIcon(a.scopeType)"
                [size]="14"
                [class]="scopeIconClass(a.scopeType)"
              />
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="text-[var(--text-sm)] font-medium text-[var(--text-primary)]">
                    {{ a.connectionName }}
                  </span>
                  <span class="rounded-[var(--radius-sm)] bg-[var(--bg-tertiary)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--text-tertiary)]">
                    {{ a.marketplace }}
                  </span>
                  <span
                    class="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-[10px] font-medium"
                    [class]="scopeBadgeClass(a.scopeType)"
                  >
                    {{ ('pricing.assignments.scope.' + a.scopeType) | translate }}
                  </span>
                </div>
                @if (a.scopeType !== 'CONNECTION') {
                  <span class="text-[var(--text-xs)] text-[var(--text-secondary)]">
                    @if (a.scopeType === 'CATEGORY') {
                      {{ a.categoryName ?? '—' }}
                    } @else {
                      {{ a.offerName ?? '—' }}
                      @if (a.sellerSku) {
                        <span class="ml-1 font-mono text-[var(--text-tertiary)]">{{ a.sellerSku }}</span>
                      }
                    }
                  </span>
                }
              </div>
              @if (rbac.canWritePolicies()) {
                <button
                  type="button"
                  (click)="confirmDelete(a)"
                  class="shrink-0 cursor-pointer rounded p-1 text-[var(--text-tertiary)] transition-colors hover:bg-[var(--status-error)]/10 hover:text-[var(--status-error)]"
                >
                  <lucide-icon [name]="icons.Trash2" [size]="14" />
                </button>
              }
            </div>
          }
        </div>
      }
    </section>

    <dp-confirmation-modal
      [open]="showDeleteModal()"
      [title]="'pricing.assignments.delete_title' | translate"
      [message]="'pricing.assignments.delete_message' | translate"
      [confirmLabel]="'pricing.assignments.delete_confirm' | translate"
      [danger]="true"
      (confirmed)="executeDelete()"
      (cancelled)="showDeleteModal.set(false)"
    />
  `,
})
export class PolicyAssignmentsSectionComponent {
  private readonly connectionApi = inject(ConnectionApiService);
  private readonly pricingApi = inject(PricingApiService);
  private readonly wsStore = inject(WorkspaceContextStore);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly queryClient = inject(QueryClient);
  protected readonly rbac = inject(RbacService);

  readonly policyId = input.required<number>();

  readonly icons = { Globe, FolderTree, Package, Trash2, Plus, X, Search };

  readonly scopeOptions: ScopeOption[] = [
    {
      value: 'CONNECTION',
      labelKey: 'pricing.assignments.scope.CONNECTION',
      descKey: 'pricing.form.assignments.scope_desc.CONNECTION',
      icon: Globe,
    },
    {
      value: 'CATEGORY',
      labelKey: 'pricing.assignments.scope.CATEGORY',
      descKey: 'pricing.form.assignments.scope_desc.CATEGORY',
      icon: FolderTree,
    },
    {
      value: 'SKU',
      labelKey: 'pricing.assignments.scope.SKU',
      descKey: 'pricing.form.assignments.scope_desc.SKU',
      icon: Package,
    },
  ];

  readonly showAddForm = signal(false);
  readonly showDeleteModal = signal(false);
  readonly deleteTarget = signal<PolicyAssignment | null>(null);

  readonly formScopeType = signal<AssignmentScopeType>('CONNECTION');
  readonly formConnectionId = signal<number | null>(null);
  readonly formCategoryId = signal<number | null>(null);
  readonly selectedOffer = signal<OfferSuggestion | null>(null);
  readonly offerSearchText = signal('');
  readonly offerResults = signal<OfferSuggestion[]>([]);

  private readonly offerSearch$ = new Subject<string>();

  constructor() {
    this.offerSearch$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((term) => {
          if (!term || term.length < 2 || !this.formConnectionId()) return of([]);
          return lastValueFrom(
            this.pricingApi.searchAssignmentOffers(
              this.wsStore.currentWorkspaceId()!,
              this.policyId(),
              this.formConnectionId()!,
              term,
            ),
          ).then((r) => r);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((results) => this.offerResults.set(results));
  }

  private readonly connectionsQuery = injectQuery(() => ({
    queryKey: ['connections'],
    queryFn: () => lastValueFrom(this.connectionApi.listConnections()),
    staleTime: 60_000,
  }));

  readonly activeConnections = computed(() =>
    (this.connectionsQuery.data() ?? []).filter(
      (c: ConnectionSummary) => c.status === 'ACTIVE',
    ),
  );

  readonly assignmentsQuery = injectQuery(() => ({
    queryKey: ['assignments', this.wsStore.currentWorkspaceId(), this.policyId()],
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

  readonly categoriesQuery = injectQuery(() => ({
    queryKey: [
      'assignment-categories',
      this.formConnectionId(),
      this.policyId(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.pricingApi.listAssignmentCategories(
          this.wsStore.currentWorkspaceId()!,
          this.policyId(),
          this.formConnectionId()!,
        ),
      ),
    enabled:
      this.formScopeType() === 'CATEGORY' &&
      !!this.formConnectionId() &&
      !!this.wsStore.currentWorkspaceId(),
    staleTime: 60_000,
  }));

  readonly rows = computed(() => this.assignmentsQuery.data() ?? []);

  protected readonly createMutation = injectMutation(() => ({
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
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success(this.translate.instant('pricing.assignments.created'));
    },
    onError: () =>
      this.toast.error(
        this.translate.instant('pricing.assignments.create_error'),
      ),
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
      this.queryClient.invalidateQueries({ queryKey: ['policies'] });
      this.toast.success(this.translate.instant('pricing.assignments.deleted'));
    },
    onError: () => {
      this.showDeleteModal.set(false);
      this.toast.error(
        this.translate.instant('pricing.assignments.delete_error'),
      );
    },
  }));

  onScopeChange(scope: AssignmentScopeType): void {
    this.formScopeType.set(scope);
    this.formCategoryId.set(null);
    this.selectedOffer.set(null);
    this.offerSearchText.set('');
    this.offerResults.set([]);
  }

  onConnectionChange(id: number | null): void {
    this.formConnectionId.set(id);
    this.formCategoryId.set(null);
    this.selectedOffer.set(null);
    this.offerSearchText.set('');
    this.offerResults.set([]);
  }

  onOfferSearch(text: string): void {
    this.offerSearchText.set(text);
    this.selectedOffer.set(null);
    this.offerSearch$.next(text);
  }

  selectOffer(offer: OfferSuggestion): void {
    this.selectedOffer.set(offer);
    this.offerSearchText.set(offer.name ?? offer.sellerSku);
    this.offerResults.set([]);
  }

  clearOffer(): void {
    this.selectedOffer.set(null);
    this.offerSearchText.set('');
    this.offerResults.set([]);
  }

  isFormValid(): boolean {
    if (!this.formConnectionId()) return false;
    if (this.formScopeType() === 'CATEGORY' && !this.formCategoryId()) return false;
    if (this.formScopeType() === 'SKU' && !this.selectedOffer()) return false;
    return true;
  }

  submitCreate(): void {
    if (!this.isFormValid()) return;
    const req: CreateAssignmentRequest = {
      connectionId: this.formConnectionId()!,
      scopeType: this.formScopeType(),
    };
    if (this.formScopeType() === 'CATEGORY') {
      req.categoryId = this.formCategoryId()!;
    }
    if (this.formScopeType() === 'SKU') {
      req.marketplaceOfferId = this.selectedOffer()!.id;
    }
    this.createMutation.mutate(req);
  }

  cancelAdd(): void {
    this.resetForm();
  }

  confirmDelete(assignment: PolicyAssignment): void {
    this.deleteTarget.set(assignment);
    this.showDeleteModal.set(true);
  }

  executeDelete(): void {
    const target = this.deleteTarget();
    if (target) this.deleteMutation.mutate(target.id);
  }

  scopeIcon(scope: AssignmentScopeType) {
    return scope === 'CONNECTION' ? Globe : scope === 'CATEGORY' ? FolderTree : Package;
  }

  scopeIconClass(scope: AssignmentScopeType): string {
    return scope === 'CONNECTION'
      ? 'text-[var(--status-info)]'
      : scope === 'CATEGORY'
        ? 'text-[var(--status-warning)]'
        : 'text-[var(--status-success)]';
  }

  scopeBadgeClass(scope: AssignmentScopeType): string {
    return scope === 'CONNECTION'
      ? 'bg-[var(--status-info)]/10 text-[var(--status-info)]'
      : scope === 'CATEGORY'
        ? 'bg-[var(--status-warning)]/10 text-[var(--status-warning)]'
        : 'bg-[var(--status-success)]/10 text-[var(--status-success)]';
  }

  private resetForm(): void {
    this.showAddForm.set(false);
    this.formScopeType.set('CONNECTION');
    this.formConnectionId.set(null);
    this.formCategoryId.set(null);
    this.selectedOffer.set(null);
    this.offerSearchText.set('');
    this.offerResults.set([]);
  }
}
