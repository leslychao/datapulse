import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, Upload, Download } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { CostProfileApiService } from '@core/api/cost-profile-api.service';
import { CostProfile, CostProfileImportResult } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { FormModalComponent } from '@shared/components/form-modal.component';
import { ConfirmationModalComponent } from '@shared/components/confirmation-modal.component';
import { DateFormatPipe } from '@shared/pipes/date-format.pipe';

@Component({
  selector: 'dp-cost-profiles-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    LucideAngularModule,
    TranslatePipe,
    SpinnerComponent,
    EmptyStateComponent,
    FormModalComponent,
    ConfirmationModalComponent,
    DateFormatPipe,
  ],
  template: `
    <div class="max-w-5xl">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.cost_profiles.title' | translate }}</h1>
          <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.cost_profiles.subtitle' | translate }}</p>
        </div>
        <div class="flex items-center gap-2">
          <button
            (click)="showAddModal.set(true)"
            class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--accent-primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-primary-hover)]"
          >
            <lucide-icon [img]="PlusIcon" [size]="16" />
            {{ 'settings.cost_profiles.add' | translate }}
          </button>
          <label
            class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] px-4 py-2 text-sm font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)]"
          >
            <lucide-icon [img]="UploadIcon" [size]="16" />
            {{ 'settings.cost_profiles.import_csv' | translate }}
            <input type="file" accept=".csv" class="hidden" (change)="onFileSelected($event)" />
          </label>
          <button
            (click)="exportCsv()"
            [disabled]="exportMutation.isPending()"
            class="flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-[var(--border-default)] px-4 py-2 text-sm font-medium text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:opacity-50"
          >
            <lucide-icon [img]="DownloadIcon" [size]="16" />
            {{ 'settings.cost_profiles.export_csv' | translate }}
          </button>
        </div>
      </div>

      <div class="mb-4">
        <input
          type="text"
          [(ngModel)]="searchQuery"
          (ngModelChange)="onSearch()"
          [placeholder]="'settings.cost_profiles.search_placeholder' | translate"
          class="w-full max-w-sm rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
        />
      </div>

      @if (profilesQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (profilesQuery.data(); as profiles) {
        @if (profiles.length === 0) {
          <dp-empty-state
            [message]="'settings.cost_profiles.empty' | translate"
            [hint]="'settings.cost_profiles.empty_hint' | translate"
          />
        } @else {
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.cost_profiles.col_sku' | translate }}</th>
                  <th class="px-4 py-2 text-left font-medium text-[var(--text-secondary)]">{{ 'settings.cost_profiles.col_product_name' | translate }}</th>
                  <th class="px-4 py-2 text-right font-medium text-[var(--text-secondary)]">{{ 'settings.cost_profiles.col_cost_price' | translate }}</th>
                  <th class="px-4 py-2 text-right font-medium text-[var(--text-secondary)]">{{ 'settings.cost_profiles.col_updated_at' | translate }}</th>
                  <th class="w-20 px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                @for (cp of profiles; track cp.id) {
                  <tr class="border-b border-[var(--border-subtle)] transition-colors hover:bg-[var(--bg-secondary)]">
                    <td class="px-4 py-2.5 font-mono text-[var(--text-primary)]">{{ cp.skuCode }}</td>
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ cp.productName }}</td>
                    <td class="px-4 py-2.5 text-right">
                      @if (editingId() === cp.id) {
                        <input
                          type="number"
                          [ngModel]="editCostValue"
                          (ngModelChange)="editCostValue = $event"
                          (blur)="saveInlineEdit(cp)"
                          (keydown.enter)="saveInlineEdit(cp)"
                          (keydown.escape)="cancelInlineEdit()"
                          step="0.01"
                          min="0"
                          class="w-28 rounded-[var(--radius-sm)] border border-[var(--accent-primary)] bg-[var(--bg-primary)] px-2 py-1 text-right font-mono text-sm text-[var(--text-primary)] outline-none"
                        />
                      } @else {
                        <span
                          (dblclick)="startInlineEdit(cp)"
                          class="cursor-pointer font-mono text-[var(--text-primary)]"
                          [class.text-[var(--text-tertiary)]]="cp.costPrice == null"
                        >
                          {{ cp.costPrice != null ? (cp.costPrice + ' ₽') : ('settings.cost_profiles.not_set' | translate) }}
                        </span>
                      }
                    </td>
                    <td class="px-4 py-2.5 text-right text-[var(--text-secondary)]">
                      {{ cp.updatedAt | dpDateFormat:'short' }}
                    </td>
                    <td class="px-4 py-2.5 text-right">
                      <button
                        (click)="confirmDelete(cp)"
                        class="cursor-pointer text-sm text-[var(--status-error)] transition-colors hover:underline"
                      >
                        {{ 'actions.delete' | translate }}
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }

      <dp-form-modal
        [title]="'settings.cost_profiles.add_title' | translate"
        [isOpen]="showAddModal()"
        [submitLabel]="'settings.cost_profiles.add' | translate"
        [isPending]="createMutation.isPending()"
        [submitDisabled]="!isAddFormValid()"
        (submit)="submitAdd()"
        (close)="closeAddModal()"
      >
        <div class="space-y-4">
          <div>
            <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.cost_profiles.sku_label' | translate }}</label>
            <input
              type="number"
              [(ngModel)]="addForm.sellerSkuId"
              placeholder="ID"
              class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            />
          </div>
          <div>
            <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.cost_profiles.cost_label' | translate }}</label>
            <input
              type="number"
              [(ngModel)]="addForm.costPrice"
              step="0.01"
              min="0.01"
              placeholder="0.00"
              class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm font-mono text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            />
          </div>
          <div>
            <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ 'settings.cost_profiles.valid_from_label' | translate }}</label>
            <input
              type="date"
              [(ngModel)]="addForm.validFrom"
              class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            />
          </div>
        </div>
      </dp-form-modal>

      <dp-form-modal
        [title]="'settings.cost_profiles.import_result_title' | translate"
        [isOpen]="showImportResult()"
        [submitLabel]="'actions.close' | translate"
        cancelLabel=""
        (submit)="showImportResult.set(false)"
        (close)="showImportResult.set(false)"
      >
        @if (importResult(); as result) {
          <div class="space-y-3">
            <p class="text-sm text-[var(--status-success)]">✓ {{ 'settings.cost_profiles.import_imported' | translate }}: {{ result.imported }}</p>
            @if (result.skipped > 0) {
              <p class="text-sm text-[var(--status-warning)]">⚠ {{ 'settings.cost_profiles.import_skipped' | translate }}: {{ result.skipped }}</p>
            }
            @if (result.errors.length > 0) {
              <p class="text-sm text-[var(--status-error)]">✕ {{ 'settings.cost_profiles.import_errors' | translate }}: {{ result.errors.length }}</p>
              <ul class="mt-2 space-y-1 text-sm text-[var(--text-secondary)]">
                @for (err of result.errors; track err.row) {
                  <li>• {{ 'settings.cost_profiles.import_error_row' | translate }}: {{ err.row }} — {{ err.message }}</li>
                }
              </ul>
            }
          </div>
        }
      </dp-form-modal>

      <dp-confirmation-modal
        [open]="showDeleteModal()"
        [title]="'settings.cost_profiles.delete_title' | translate"
        [message]="translate.instant('settings.cost_profiles.delete_message', { sku: profileToDelete()?.skuCode || '' })"
        [confirmLabel]="'actions.delete' | translate"
        [danger]="true"
        (confirmed)="doDelete()"
        (cancelled)="showDeleteModal.set(false)"
      />
    </div>
  `,
})
export class CostProfilesPageComponent {
  protected readonly PlusIcon = Plus;
  protected readonly UploadIcon = Upload;
  protected readonly DownloadIcon = Download;

  private readonly costProfileApi = inject(CostProfileApiService);
  private readonly toast = inject(ToastService);
  protected readonly translate = inject(TranslateService);

  readonly showAddModal = signal(false);
  readonly showDeleteModal = signal(false);
  readonly showImportResult = signal(false);
  readonly importResult = signal<CostProfileImportResult | null>(null);
  readonly profileToDelete = signal<CostProfile | null>(null);
  readonly editingId = signal<number | null>(null);

  searchQuery = '';
  editCostValue: number | null = null;

  addForm = {
    sellerSkuId: null as number | null,
    costPrice: null as number | null,
    validFrom: new Date().toISOString().slice(0, 10),
  };

  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly profilesQuery = injectQuery(() => ({
    queryKey: ['cost-profiles', this.searchQuery],
    queryFn: () => lastValueFrom(this.costProfileApi.listCostProfiles(this.searchQuery || undefined)),
  }));

  readonly createMutation = injectMutation(() => ({
    mutationFn: (req: { sellerSkuId: number; costPrice: number; currency: string; validFrom: string }) =>
      lastValueFrom(this.costProfileApi.createCostProfile(req)),
    onSuccess: () => {
      this.profilesQuery.refetch();
      this.closeAddModal();
      this.toast.success(this.translate.instant('settings.cost_profiles.added'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.cost_profiles.error_add')),
  }));

  readonly updateMutation = injectMutation(() => ({
    mutationFn: (vars: { id: number; costPrice: number }) =>
      lastValueFrom(
        this.costProfileApi.updateCostProfile(vars.id, {
          costPrice: vars.costPrice,
          currency: 'RUB',
          validFrom: new Date().toISOString().slice(0, 10),
        }),
      ),
    onSuccess: () => {
      this.profilesQuery.refetch();
      this.editingId.set(null);
      this.toast.success(this.translate.instant('settings.cost_profiles.updated'));
    },
    onError: () => {
      this.editingId.set(null);
      this.toast.error(this.translate.instant('settings.cost_profiles.error_save'));
    },
  }));

  readonly deleteMutation = injectMutation(() => ({
    mutationFn: (id: number) => lastValueFrom(this.costProfileApi.deleteCostProfile(id)),
    onSuccess: () => {
      this.profilesQuery.refetch();
      this.showDeleteModal.set(false);
      this.toast.success(this.translate.instant('settings.cost_profiles.deleted'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.cost_profiles.error_delete')),
  }));

  readonly importMutation = injectMutation(() => ({
    mutationFn: (file: File) => lastValueFrom(this.costProfileApi.importCsv(file)),
    onSuccess: (result: CostProfileImportResult) => {
      this.profilesQuery.refetch();
      this.importResult.set(result);
      this.showImportResult.set(true);
    },
    onError: () => this.toast.error(this.translate.instant('settings.cost_profiles.import_failed')),
  }));

  readonly exportMutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.costProfileApi.exportCsv()),
    onSuccess: (blob: Blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'cost-profiles.csv';
      a.click();
      URL.revokeObjectURL(url);
      this.toast.success(this.translate.instant('settings.cost_profiles.export_done'));
    },
    onError: () => this.toast.error(this.translate.instant('settings.cost_profiles.error_export')),
  }));

  onSearch(): void {
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      this.profilesQuery.refetch();
    }, 300);
  }

  isAddFormValid(): boolean {
    return (
      this.addForm.sellerSkuId != null &&
      this.addForm.sellerSkuId > 0 &&
      this.addForm.costPrice != null &&
      this.addForm.costPrice > 0 &&
      !!this.addForm.validFrom
    );
  }

  submitAdd(): void {
    if (!this.isAddFormValid()) return;
    this.createMutation.mutate({
      sellerSkuId: this.addForm.sellerSkuId!,
      costPrice: this.addForm.costPrice!,
      currency: 'RUB',
      validFrom: this.addForm.validFrom,
    });
  }

  closeAddModal(): void {
    this.showAddModal.set(false);
    this.addForm = {
      sellerSkuId: null,
      costPrice: null,
      validFrom: new Date().toISOString().slice(0, 10),
    };
  }

  startInlineEdit(cp: CostProfile): void {
    this.editingId.set(cp.id);
    this.editCostValue = cp.costPrice;
  }

  saveInlineEdit(cp: CostProfile): void {
    if (this.editCostValue == null || this.editCostValue <= 0) {
      this.cancelInlineEdit();
      return;
    }
    if (this.editCostValue === cp.costPrice) {
      this.cancelInlineEdit();
      return;
    }
    this.updateMutation.mutate({ id: cp.id, costPrice: this.editCostValue });
  }

  cancelInlineEdit(): void {
    this.editingId.set(null);
    this.editCostValue = null;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      this.toast.error(this.translate.instant('settings.cost_profiles.file_too_large'));
      return;
    }

    this.importMutation.mutate(file);
    input.value = '';
  }

  exportCsv(): void {
    this.toast.info(this.translate.instant('settings.cost_profiles.export_preparing'));
    this.exportMutation.mutate(undefined as never);
  }

  confirmDelete(cp: CostProfile): void {
    this.profileToDelete.set(cp);
    this.showDeleteModal.set(true);
  }

  doDelete(): void {
    const cp = this.profileToDelete();
    if (!cp) return;
    this.deleteMutation.mutate(cp.id);
  }
}
