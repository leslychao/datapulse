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
import { LucideAngularModule, Plus, Upload, Download } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AgGridAngular } from 'ag-grid-angular';
import {
  ColDef,
  CellValueChangedEvent,
  GetRowIdParams,
} from 'ag-grid-community';

import { CostProfileApiService } from '@core/api/cost-profile-api.service';
import { RbacService } from '@core/auth/rbac.service';
import { CostProfile, CostProfilePage, CostProfileImportResult } from '@core/models';
import { ToastService } from '@shared/shell/toast/toast.service';
import { SpinnerComponent } from '@shared/layout/spinner.component';
import { EmptyStateComponent } from '@shared/components/empty-state.component';
import { FormModalComponent } from '@shared/components/form-modal.component';
import { AG_GRID_LOCALE_RU } from '@shared/config/ag-grid-locale';

@Component({
  selector: 'dp-cost-profiles-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    LucideAngularModule,
    TranslatePipe,
    AgGridAngular,
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
          class="w-full max-w-sm rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] px-3 py-2 text-sm text-[var(--text-primary)] outline-none placeholder:text-[var(--text-tertiary)] focus:border-[var(--accent-primary)]"
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
          <div class="overflow-hidden rounded-[var(--radius-md)] border border-[var(--border-default)]" style="height: 520px">
            <ag-grid-angular
              class="ag-theme-alpine h-full w-full"
              [columnDefs]="colDefs()"
              [rowData]="page.content"
              [pagination]="false"
              [getRowId]="getRowId"
              [suppressCellFocus]="false"
              [animateRows]="false"
              [localeText]="localeText"
              [singleClickEdit]="false"
              (cellValueChanged)="onCellValueChanged($event)"
            ></ag-grid-angular>
          </div>

          <div class="mt-3 flex items-center justify-between text-sm text-[var(--text-secondary)]">
            <span>
              {{ pageRangeLabel() }}
            </span>
            <div class="flex items-center gap-2">
              <button
                [disabled]="currentPage() === 0"
                (click)="goToPage(currentPage() - 1)"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-default disabled:opacity-40"
              >
                ← {{ 'common.prev' | translate }}
              </button>
              <button
                [disabled]="currentPage() >= page.totalPages - 1"
                (click)="goToPage(currentPage() + 1)"
                class="cursor-pointer rounded-[var(--radius-md)] border border-[var(--border-default)] px-3 py-1 text-sm text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-tertiary)] disabled:cursor-default disabled:opacity-40"
              >
                {{ 'common.next' | translate }} →
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

  protected readonly localeText = AG_GRID_LOCALE_RU;

  readonly getRowId = (params: GetRowIdParams<CostProfile>) =>
    String(params.data.id);

  readonly colDefs = computed<ColDef<CostProfile>[]>(() => {
    const canEdit = this.rbac.canEditCostProfiles();
    return [
      {
        headerName: this.translate.instant('settings.cost_profiles.col_sku'),
        field: 'skuCode',
        width: 140,
        cellClass: 'font-mono',
        sortable: true,
      },
      {
        headerName: this.translate.instant('settings.cost_profiles.col_product_name'),
        field: 'productName',
        flex: 1,
        sortable: true,
      },
      {
        headerName: this.translate.instant('settings.cost_profiles.col_cost_price'),
        field: 'costPrice',
        width: 120,
        cellClass: 'font-mono text-right',
        headerClass: 'ag-right-aligned-header',
        editable: canEdit,
        singleClickEdit: false,
        valueFormatter: (p) =>
          p.value != null
            ? `${p.value} ₽`
            : this.translate.instant('settings.cost_profiles.not_set'),
        valueParser: (p) => {
          const val = parseFloat(p.newValue);
          return isNaN(val) || val <= 0 ? p.oldValue : val;
        },
        sortable: true,
      },
      {
        headerName: this.translate.instant('settings.cost_profiles.col_updated_at'),
        field: 'updatedAt',
        width: 100,
        headerClass: 'ag-right-aligned-header',
        cellClass: 'text-right text-[var(--text-secondary)]',
        valueFormatter: (p) => {
          if (!p.value) return '—';
          const d = new Date(p.value);
          return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
        },
        sortable: true,
      },
    ];
  });

  readonly profilesQuery = injectQuery(() => ({
    queryKey: ['cost-profiles', this.searchQuery, this.currentPage()],
    queryFn: () =>
      lastValueFrom(
        this.costProfileApi.listCostProfiles(
          this.searchQuery || undefined,
          this.currentPage(),
          this.pageSize,
        ),
      ),
  }));

  readonly pageRangeLabel = computed(() => {
    const page = this.profilesQuery.data();
    if (!page) return '';
    const start = page.number * page.size + 1;
    const end = Math.min(start + page.content.length - 1, page.totalElements);
    return `${start}–${end} из ${page.totalElements}`;
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
      this.currentPage.set(0);
      this.profilesQuery.refetch();
    }, 300);
  }

  goToPage(page: number): void {
    this.currentPage.set(page);
  }

  onCellValueChanged(event: CellValueChangedEvent<CostProfile>): void {
    if (event.colDef.field !== 'costPrice') return;
    const row = event.data;
    const newVal = event.newValue;
    if (newVal == null || newVal <= 0 || newVal === event.oldValue) {
      this.profilesQuery.refetch();
      return;
    }
    this.updateMutation.mutate({ id: row.id, costPrice: newVal });
  }

  onSkuSearch(query: string): void {
    this.addForm.sellerSkuId = null;
    if (this.skuSearchTimeout) clearTimeout(this.skuSearchTimeout);
    if (!query || query.length < 2) {
      this.skuSuggestions.set([]);
      return;
    }
    this.skuSearchTimeout = setTimeout(() => {
      lastValueFrom(this.costProfileApi.listCostProfiles(query, 0, 10)).then(
        (page) =>
          this.skuSuggestions.set(
            page.content.map((cp) => ({
              sellerSkuId: cp.sellerSkuId,
              skuCode: cp.skuCode,
              productName: cp.productName,
            })),
          ),
        () => this.skuSuggestions.set([]),
      );
    }, 200);
  }

  selectSku(s: { sellerSkuId: number; skuCode: string; productName: string }): void {
    this.addForm.sellerSkuId = s.sellerSkuId;
    this.addForm.skuSearch = `${s.skuCode} — ${s.productName}`;
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
