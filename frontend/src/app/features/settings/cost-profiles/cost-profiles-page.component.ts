import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { injectQuery, injectMutation } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, Upload, Download, ArrowUp, ArrowDown } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { CostProfileApiService } from '@core/api/cost-profile-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { CostProfile, CostProfileImportResult } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { FormModalComponent } from '@shared/components/form-modal.component';

type CostProfileSortField = 'skuCode' | 'productName' | 'costPrice' | 'updatedAt';

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
  ],
  template: `
    <div class="max-w-5xl">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-[var(--text-xl)] font-semibold text-[var(--text-primary)]">{{ 'settings.cost_profiles.title' | translate }}</h1>
          <p class="mt-1 text-[var(--text-sm)] text-[var(--text-secondary)]">{{ 'settings.cost_profiles.subtitle' | translate }}</p>
        </div>
        <div class="flex items-center gap-2">
          @if (rbac.canEditCostProfiles()) {
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
          }
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
          class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
        />
      </div>

      @if (profilesQuery.isPending()) {
        <dp-spinner [message]="'common.loading' | translate" />
      }

      @if (profilesQuery.data(); as page) {
        @if (page.content.length === 0 && currentPage() === 0) {
          <dp-empty-state
            [message]="'settings.cost_profiles.empty' | translate"
            [hint]="'settings.cost_profiles.empty_hint' | translate"
          />
        } @else {
          <div
            class="overflow-x-auto rounded-[var(--radius-lg)] border border-[var(--border-default)] bg-[var(--bg-primary)]"
          >
            <table class="table-auto w-full border-collapse text-sm">
              <thead>
                <tr class="border-b border-[var(--border-default)] bg-[var(--bg-secondary)]">
                  <th
                    class="w-0 border-r border-[var(--border-default)] p-0 last:border-r-0"
                    [attr.aria-sort]="sortAriaSort('skuCode')"
                  >
                    <button
                      type="button"
                      class="flex w-full cursor-pointer items-center gap-1 whitespace-nowrap px-4 py-2.5 text-left text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                      (click)="toggleSort('skuCode')"
                    >
                      <span>{{ 'settings.cost_profiles.col_sku' | translate }}</span>
                      @if (sortField() === 'skuCode') {
                        <lucide-icon
                          [img]="sortDir() === 'asc' ? ArrowUpIcon : ArrowDownIcon"
                          [size]="14"
                          class="shrink-0 text-[var(--text-tertiary)]"
                        />
                      }
                    </button>
                  </th>
                  <th
                    class="border-r border-[var(--border-default)] p-0 last:border-r-0"
                    [attr.aria-sort]="sortAriaSort('productName')"
                  >
                    <button
                      type="button"
                      class="flex w-full cursor-pointer items-center gap-1 px-4 py-2.5 text-left text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                      (click)="toggleSort('productName')"
                    >
                      <span>{{ 'settings.cost_profiles.col_product_name' | translate }}</span>
                      @if (sortField() === 'productName') {
                        <lucide-icon
                          [img]="sortDir() === 'asc' ? ArrowUpIcon : ArrowDownIcon"
                          [size]="14"
                          class="shrink-0 text-[var(--text-tertiary)]"
                        />
                      }
                    </button>
                  </th>
                  <th
                    class="w-0 border-r border-[var(--border-default)] p-0 last:border-r-0"
                    [attr.aria-sort]="sortAriaSort('costPrice')"
                  >
                    <button
                      type="button"
                      class="flex w-full cursor-pointer items-center justify-end gap-1 whitespace-nowrap px-4 py-2.5 text-right text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                      (click)="toggleSort('costPrice')"
                    >
                      @if (sortField() === 'costPrice') {
                        <lucide-icon
                          [img]="sortDir() === 'asc' ? ArrowUpIcon : ArrowDownIcon"
                          [size]="14"
                          class="shrink-0 text-[var(--text-tertiary)]"
                        />
                      }
                      <span>{{ 'settings.cost_profiles.col_cost_price' | translate }}</span>
                    </button>
                  </th>
                  <th class="w-0 p-0" [attr.aria-sort]="sortAriaSort('updatedAt')">
                    <button
                      type="button"
                      class="flex w-full cursor-pointer items-center justify-end gap-1 whitespace-nowrap px-4 py-2.5 text-right text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-tertiary)]"
                      (click)="toggleSort('updatedAt')"
                    >
                      @if (sortField() === 'updatedAt') {
                        <lucide-icon
                          [img]="sortDir() === 'asc' ? ArrowUpIcon : ArrowDownIcon"
                          [size]="14"
                          class="shrink-0 text-[var(--text-tertiary)]"
                        />
                      }
                      <span>{{ 'settings.cost_profiles.col_updated_at' | translate }}</span>
                    </button>
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (row of page.content; track row.id) {
                  <tr class="border-b border-[var(--border-subtle)] transition-colors last:border-b-0 hover:bg-[var(--bg-secondary)]">
                    <td class="whitespace-nowrap px-4 py-2.5 font-mono text-[var(--text-primary)]">
                      {{ row.skuCode }}
                    </td>
                    <td class="px-4 py-2.5 text-[var(--text-primary)]">{{ row.productName }}</td>
                    <td class="whitespace-nowrap px-4 py-2.5 text-right font-mono align-top">
                      @if (editingRowId() === row.id && rbac.canEditCostProfiles()) {
                        <input
                          type="number"
                          [(ngModel)]="draftCostValue"
                          step="0.01"
                          min="0.01"
                          (blur)="commitCostEdit(row)"
                          (keydown.enter)="blurCostInput($event)"
                          (keydown.escape)="cancelCostEdit($event)"
                          class="box-border min-w-[5.5rem] rounded-[var(--radius-sm)] border border-[var(--accent-primary)] bg-[var(--bg-primary)] px-2 py-1 text-right text-sm font-mono text-[var(--text-primary)] outline-none"
                        />
                      } @else {
                        <span
                          class="inline-block min-h-[1.25rem]"
                          [class.cursor-text]="rbac.canEditCostProfiles()"
                          [class.select-none]="rbac.canEditCostProfiles()"
                          [title]="
                            rbac.canEditCostProfiles()
                              ? ('settings.cost_profiles.edit_cost_hint' | translate)
                              : ''
                          "
                          (dblclick)="beginCostEdit(row)"
                        >
                          @if (row.costPrice != null) {
                            {{ row.costPrice }} ₽
                          } @else {
                            <span class="text-[var(--text-tertiary)]">{{
                              'settings.cost_profiles.not_set' | translate
                            }}</span>
                          }
                        </span>
                      }
                    </td>
                    <td class="whitespace-nowrap px-4 py-2.5 text-right text-[var(--text-secondary)]">
                      {{ formatUpdatedAt(row.updatedAt) }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="mt-4 flex items-center justify-between text-sm text-[var(--text-secondary)]">
            <span>{{ 'pagination.showing' | translate: paginationSummary() }}</span>
            <div class="flex items-center gap-2">
              <button
                type="button"
                [disabled]="currentPage() === 0"
                (click)="goToPage(currentPage() - 1)"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                ← {{ 'pagination.prev' | translate }}
              </button>
              <button
                type="button"
                [disabled]="currentPage() >= page.totalPages - 1"
                (click)="goToPage(currentPage() + 1)"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-1.5 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {{ 'pagination.next' | translate }} →
              </button>
            </div>
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
              type="text"
              [(ngModel)]="addForm.skuSearch"
              (ngModelChange)="onSkuSearch($event)"
              [placeholder]="'settings.cost_profiles.sku_search_placeholder' | translate"
              class="w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none focus:border-[var(--accent-primary)]"
            />
            @if (skuSuggestions().length > 0 && !addForm.sellerSkuId) {
              <ul class="mt-1 max-h-40 overflow-y-auto rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)]">
                @for (s of skuSuggestions(); track s.sellerSkuId) {
                  <li
                    (click)="selectSku(s)"
                    class="cursor-pointer px-3 py-2 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-secondary)]"
                  >
                    <span class="font-mono">{{ s.skuCode }}</span>
                    <span class="ml-2 text-[var(--text-secondary)]">{{ s.productName }}</span>
                  </li>
                }
              </ul>
            }
            @if (addForm.sellerSkuId) {
              <p class="mt-1 text-xs text-[var(--status-success)]">
                ✓ {{ addForm.skuSearch }}
              </p>
            }
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

    </div>
  `,
})
export class CostProfilesPageComponent {
  protected readonly PlusIcon = Plus;
  protected readonly UploadIcon = Upload;
  protected readonly DownloadIcon = Download;
  protected readonly ArrowUpIcon = ArrowUp;
  protected readonly ArrowDownIcon = ArrowDown;

  private readonly costProfileApi = inject(CostProfileApiService);
  private readonly toast = inject(ToastService);
  protected readonly translate = inject(TranslateService);
  protected readonly rbac = inject(RbacService);

  readonly showAddModal = signal(false);
  readonly showImportResult = signal(false);
  readonly importResult = signal<CostProfileImportResult | null>(null);

  readonly currentPage = signal(0);
  private readonly pageSize = 50;

  searchQuery = '';
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  addForm = {
    skuSearch: '',
    sellerSkuId: null as number | null,
    costPrice: null as number | null,
    validFrom: new Date().toISOString().slice(0, 10),
  };

  readonly skuSuggestions = signal<{ sellerSkuId: number; skuCode: string; productName: string }[]>([]);
  private skuSearchTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly editingRowId = signal<number | null>(null);
  draftCostValue: string | number = '';

  readonly sortField = signal<CostProfileSortField>('skuCode');
  readonly sortDir = signal<'asc' | 'desc'>('asc');

  readonly profilesQuery = injectQuery(() => ({
    queryKey: [
      'cost-profiles',
      this.searchQuery,
      this.currentPage(),
      this.sortField(),
      this.sortDir(),
    ],
    queryFn: () =>
      lastValueFrom(
        this.costProfileApi.listCostProfiles(
          this.searchQuery || undefined,
          this.currentPage(),
          this.pageSize,
          this.sortField(),
          this.sortDir(),
        ),
      ),
  }));

  toggleSort(field: CostProfileSortField): void {
    if (this.sortField() === field) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortField.set(field);
      this.sortDir.set('asc');
    }
    this.editingRowId.set(null);
    this.currentPage.set(0);
  }

  sortAriaSort(field: CostProfileSortField): 'ascending' | 'descending' | 'none' {
    if (this.sortField() !== field) {
      return 'none';
    }
    return this.sortDir() === 'asc' ? 'ascending' : 'descending';
  }

  readonly paginationSummary = computed(() => {
    const page = this.profilesQuery.data();
    if (!page) {
      return { from: 0, to: 0, total: 0 };
    }
    if (page.content.length === 0) {
      return { from: 0, to: 0, total: page.totalElements };
    }
    const from = page.number * page.size + 1;
    const to = Math.min(from + page.content.length - 1, page.totalElements);
    return { from, to, total: page.totalElements };
  });

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
      this.toast.success(this.translate.instant('settings.cost_profiles.updated'));
    },
    onError: () => {
      this.profilesQuery.refetch();
      this.toast.error(this.translate.instant('settings.cost_profiles.error_save'));
    },
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
      this.editingRowId.set(null);
      this.currentPage.set(0);
      this.profilesQuery.refetch();
    }, 300);
  }

  goToPage(page: number): void {
    this.editingRowId.set(null);
    this.currentPage.set(page);
  }

  formatUpdatedAt(iso: string | null): string {
    if (!iso) {
      return '—';
    }
    const d = new Date(iso);
    return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  }

  beginCostEdit(row: CostProfile): void {
    if (!this.rbac.canEditCostProfiles()) {
      return;
    }
    this.editingRowId.set(row.id);
    this.draftCostValue = row.costPrice != null ? row.costPrice : '';
  }

  blurCostInput(event: Event): void {
    const el = event.target;
    if (el instanceof HTMLInputElement) {
      el.blur();
    }
  }

  cancelCostEdit(event: Event): void {
    event.preventDefault();
    this.editingRowId.set(null);
    this.profilesQuery.refetch();
  }

  commitCostEdit(row: CostProfile): void {
    if (this.editingRowId() !== row.id) {
      return;
    }
    this.editingRowId.set(null);
    const raw = String(this.draftCostValue).trim().replace(',', '.');
    const val = parseFloat(raw);
    if (Number.isNaN(val) || val <= 0) {
      this.profilesQuery.refetch();
      return;
    }
    if (val === row.costPrice) {
      return;
    }
    this.updateMutation.mutate({ id: row.id, costPrice: val });
  }

  onSkuSearch(query: string): void {
    this.addForm.sellerSkuId = null;
    if (this.skuSearchTimeout) clearTimeout(this.skuSearchTimeout);
    const trimmed = query.trim();
    if (!trimmed || trimmed.length < 3) {
      this.skuSuggestions.set([]);
      return;
    }
    this.skuSearchTimeout = setTimeout(() => {
      lastValueFrom(this.costProfileApi.searchSkuSuggestions(trimmed)).then(
        (rows) => this.skuSuggestions.set(rows),
        () => this.skuSuggestions.set([]),
      );
    }, 200);
  }

  selectSku(s: { sellerSkuId: number; skuCode: string; productName: string }): void {
    this.addForm.sellerSkuId = s.sellerSkuId;
    this.addForm.skuSearch = s.skuCode;
    this.skuSuggestions.set([]);
  }

  isAddFormValid(): boolean {
    return (
      this.addForm.sellerSkuId != null &&
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
      skuSearch: '',
      sellerSkuId: null,
      costPrice: null,
      validFrom: new Date().toISOString().slice(0, 10),
    };
    this.skuSuggestions.set([]);
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
}
